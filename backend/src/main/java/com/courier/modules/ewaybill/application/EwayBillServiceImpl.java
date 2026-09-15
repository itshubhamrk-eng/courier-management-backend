package com.courier.modules.ewaybill.application;

import com.courier.modules.company.application.CompanySettingsService;
import com.courier.modules.ewaybill.application.command.CreateEwayBillCommand;
import com.courier.modules.ewaybill.application.command.EwayBillDataCommand;
import com.courier.modules.ewaybill.application.command.UpdateEwayBillCommand;
import com.courier.modules.ewaybill.application.provider.EwayBillProvider;
import com.courier.modules.ewaybill.domain.EwayBill;
import com.courier.modules.ewaybill.domain.EwayBillDocumentType;
import com.courier.modules.ewaybill.domain.EwayBillRepository;
import com.courier.modules.ewaybill.domain.EwayBillStatus;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * E-Way Bill Management use cases. See {@link EwayBillService} for the module's own
 * business rules and which entry point does what.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EwayBillServiceImpl implements EwayBillService {

    private static final String ENTITY = "EwayBill";
    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.OPERATOR + "')";
    private static final String READERS = "isAuthenticated()";

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final String DEFAULT_TRANSPORT_MODE = "ROAD";
    private static final Set<EwayBillStatus> PART_A_DONE = Set.of(
            EwayBillStatus.PART_A_GENERATED, EwayBillStatus.PART_B_PENDING, EwayBillStatus.GENERATED);

    private final EwayBillRepository repository;
    private final EwayBillProvider provider;
    private final CompanySettingsService companySettingsService;
    private final FileStoragePort fileStoragePort;
    private final AuditService auditService;

    // ------------------------------------------------------------- threshold / gate

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public BigDecimal mandatoryThreshold() {
        return companySettingsService.get().getEwayBillMandatoryValue();
    }

    @Override
    public boolean isRequired(BigDecimal invoiceValue) {
        return invoiceValue != null && invoiceValue.compareTo(mandatoryThreshold()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public void requireBookingData(BigDecimal invoiceValue, EwayBillDataCommand ewayBill) {
        if (!isRequired(invoiceValue)) {
            return;
        }
        if (ewayBill == null || isBlank(ewayBill.invoiceNumber()) || ewayBill.invoiceDate() == null) {
            throw new BusinessRuleException(mandatoryMessage());
        }
    }

    /** Exact wording the brief specifies, with the company's own threshold interpolated —
     *  the default (50000.00) renders identically to the brief's own example text. */
    private String mandatoryMessage() {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return "E-Way Bill is mandatory because invoice value exceeds ₹"
                + format.format(mandatoryThreshold()) + ".";
    }

    // ------------------------------------------------------------- Part-A (booking)

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill generatePartAForShipment(UUID shipmentId, EwayBillDataCommand data, ShipmentEwayBillContext ctx) {
        if (data == null) {
            return null;
        }
        UUID companyId = requireCompany();
        String invoiceNumber = blankToNull(data.invoiceNumber());

        // Idempotent by (shipment, invoice number): a retried booking call (e.g. a
        // client retry after a network blip) must never raise a second E-Way Bill for
        // the same invoice. Deliberately not "newest row regardless of invoice number"
        // — an edit that changes the invoice number is a new E-Way Bill, not an amend.
        EwayBill bill = repository.findAllByShipmentIdWithinCompany(shipmentId, companyId).stream()
                .filter(b -> b.getStatus() != EwayBillStatus.CANCELLED)
                .filter(b -> Objects.equals(b.getInvoiceNumber(), invoiceNumber))
                .findFirst().orElse(null);

        if (bill != null && PART_A_DONE.contains(bill.getStatus())) {
            log.info("E-Way Bill Part-A already generated for shipment {} invoice {} in company {} — skipping",
                    shipmentId, invoiceNumber, companyId);
            return bill;
        }
        if (bill == null) {
            bill = EwayBill.builder().shipmentId(shipmentId).status(EwayBillStatus.PART_A_PENDING).build();
        }

        applyData(bill, data);
        applyContext(bill, ctx);
        bill.applyInvariants();
        applyStatus(bill, EwayBillStatus.PART_A_PENDING);

        EwayBill toSave = bill;
        try {
            EwayBillProvider.PartAResult result = provider.generatePartA(toPartARequest(toSave));
            if (result != null && result.success()) {
                toSave.setEwayBillNumber(result.ewayBillNumber());
                toSave.setValidFrom(result.validFrom());
                toSave.setValidUntil(result.validUntil());
                toSave.setProviderName(result.providerName());
                toSave.setProviderReference(result.providerReference());
                toSave.setPartAGeneratedAt(Instant.now());
                toSave.setLastError(null);
                applyStatus(toSave, EwayBillStatus.PART_A_GENERATED);
                log.info("E-Way Bill {} Part-A generated for shipment {} in company {}",
                        toSave.getEwayBillNumber(), shipmentId, companyId);
                EwayBill saved = repository.save(toSave);
                auditService.record(AuditAction.EWAY_BILL_PART_A_GENERATED, ENTITY, saved.getId(),
                        Map.of("shipmentId", shipmentId.toString(), "ewayBillNumber",
                                nullToEmpty(saved.getEwayBillNumber())));
                return saved;
            }
            return failPartA(toSave, shipmentId, companyId,
                    result == null ? "No response from the E-Way Bill provider." : result.failureReason());
        } catch (Exception e) {
            log.error("E-Way Bill Part-A generation threw for shipment {} in company {}: {}",
                    shipmentId, companyId, e.getMessage());
            return failPartA(toSave, shipmentId, companyId, "E-Way Bill provider error: " + safeMessage(e));
        }
    }

    /** Never rethrown by any caller — a booking transaction must commit regardless of
     *  provider outcome; only missing input data ({@link #requireBookingData}) may
     *  legitimately block a booking. */
    private EwayBill failPartA(EwayBill bill, UUID shipmentId, UUID companyId, String reason) {
        bill.setLastError(reason);
        bill.setRetryCount(bill.getRetryCount() + 1);
        applyStatus(bill, EwayBillStatus.FAILED);
        EwayBill saved = repository.save(bill);
        log.warn("E-Way Bill Part-A failed for shipment {} in company {}: {}", shipmentId, companyId, reason);
        auditService.record(AuditAction.EWAY_BILL_GENERATION_FAILED, ENTITY, saved.getId(),
                Map.of("shipmentId", shipmentId.toString(), "stage", "PART_A", "reason", nullToEmpty(reason)));
        return saved;
    }

    // ------------------------------------------------------------- Part-B (dispatch)

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public void triggerPartBForShipments(Collection<UUID> shipmentIds, String vehicleNumber,
                                         String transporterId, String transportMode) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            return;
        }
        UUID companyId = requireCompany();
        String mode = isBlank(transportMode) ? DEFAULT_TRANSPORT_MODE : transportMode;

        List<EwayBill> current = repository.findAllByShipmentIdInWithinCompany(shipmentIds, companyId).stream()
                .filter(b -> b.getStatus() != EwayBillStatus.CANCELLED)
                .collect(Collectors.groupingBy(EwayBill::getShipmentId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(EwayBill::getCreatedAt)),
                                Optional::orElseThrow)))
                .values().stream()
                .filter(b -> b.getStatus() == EwayBillStatus.PART_A_GENERATED)
                .toList();

        for (EwayBill bill : current) {
            try {
                bill.setVehicleNumber(vehicleNumber);
                if (!isBlank(transporterId)) {
                    bill.setTransporterId(transporterId);
                }
                bill.setTransportMode(mode);
                applyStatus(bill, EwayBillStatus.PART_B_PENDING);

                EwayBillProvider.PartBResult result = provider.updatePartB(new EwayBillProvider.PartBRequest(
                        bill.getEwayBillNumber(), vehicleNumber, bill.getTransporterId(), mode));

                if (result != null && result.success()) {
                    if (result.providerReference() != null) {
                        bill.setProviderReference(result.providerReference());
                    }
                    bill.setPartBGeneratedAt(Instant.now());
                    bill.setLastError(null);
                    applyStatus(bill, EwayBillStatus.GENERATED);
                    repository.save(bill);
                    auditService.record(AuditAction.EWAY_BILL_PART_B_GENERATED, ENTITY, bill.getId(),
                            Map.of("shipmentId", bill.getShipmentId().toString(), "vehicleNumber", vehicleNumber));
                } else {
                    failPartB(bill, result == null ? "No response from the E-Way Bill provider." : result.failureReason());
                }
            } catch (Exception e) {
                log.error("E-Way Bill Part-B update threw for shipment {} in company {}: {}",
                        bill.getShipmentId(), companyId, e.getMessage());
                try {
                    failPartB(bill, "E-Way Bill provider error: " + safeMessage(e));
                } catch (Exception persistFailure) {
                    log.error("Could not even persist the E-Way Bill Part-B failure for shipment {}: {}",
                            bill.getShipmentId(), persistFailure.getMessage());
                }
            }
        }
    }

    private void failPartB(EwayBill bill, String reason) {
        bill.setLastError(reason);
        bill.setRetryCount(bill.getRetryCount() + 1);
        applyStatus(bill, EwayBillStatus.FAILED);
        repository.save(bill);
        log.warn("E-Way Bill Part-B failed for shipment {} in company {}: {}",
                bill.getShipmentId(), bill.getCompanyId(), reason);
        auditService.record(AuditAction.EWAY_BILL_GENERATION_FAILED, ENTITY, bill.getId(),
                Map.of("shipmentId", bill.getShipmentId().toString(), "stage", "PART_B", "reason", nullToEmpty(reason)));
    }

    // ------------------------------------------------------------- manual attach

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill upsertForShipment(UUID shipmentId, EwayBillDataCommand data) {
        if (data == null) {
            return null;
        }
        UUID companyId = requireCompany();
        EwayBill bill = repository.findAllByShipmentIdWithinCompany(shipmentId, companyId).stream()
                .filter(b -> b.getStatus() != EwayBillStatus.CANCELLED)
                .findFirst().orElse(null);
        if (bill == null) {
            bill = EwayBill.builder().shipmentId(shipmentId).status(EwayBillStatus.PART_A_PENDING).build();
        }
        applyData(bill, data);
        bill.applyInvariants();
        applyStatus(bill, isBlank(bill.getEwayBillNumber()) ? EwayBillStatus.PART_A_PENDING : EwayBillStatus.GENERATED);

        EwayBill saved = repository.save(bill);
        log.info("E-Way Bill {} ({}) for shipment {} in company {} -> {}", saved.getEwayBillNumber(),
                saved.getId(), shipmentId, companyId, saved.getStatus());
        auditService.record(AuditAction.EWAY_BILL_CREATED, ENTITY, saved.getId(),
                Map.of("shipmentId", shipmentId.toString(), "status", saved.getStatus().name()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Optional<EwayBillSnapshot> findLatestForShipment(UUID shipmentId) {
        return currentFor(shipmentId, requireCompany()).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Map<UUID, EwayBillSnapshot> findLatestForShipments(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        UUID companyId = requireCompany();
        return repository.findAllByShipmentIdInWithinCompany(shipmentIds, companyId).stream()
                .collect(Collectors.groupingBy(EwayBill::getShipmentId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> toSnapshot(e.getValue().stream().filter(b -> b.getStatus() != EwayBillStatus.CANCELLED)
                                .findFirst().orElse(e.getValue().get(0)))));
    }

    /** Newest non-cancelled row, or simply the newest if every row for this shipment has
     *  been cancelled — see the class doc on why more than one row may exist. */
    private Optional<EwayBill> currentFor(UUID shipmentId, UUID companyId) {
        List<EwayBill> all = repository.findAllByShipmentIdWithinCompany(shipmentId, companyId);
        return all.stream().filter(b -> b.getStatus() != EwayBillStatus.CANCELLED).findFirst()
                .or(() -> all.stream().findFirst());
    }

    private EwayBillSnapshot toSnapshot(EwayBill b) {
        return new EwayBillSnapshot(b.getId(), b.getEwayBillNumber(), b.getStatus().name(), b.getInvoiceNumber(),
                b.getInvoiceValue(), b.getValidFrom(), b.getValidUntil(), b.getDocumentUrl(), b.getLastError());
    }

    // ------------------------------------------------------------- standalone lifecycle

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill create(CreateEwayBillCommand command) {
        UUID companyId = requireCompany();
        EwayBill bill = EwayBill.builder().shipmentId(command.shipmentId())
                .status(EwayBillStatus.PART_A_PENDING).build();
        applyData(bill, command.data());
        bill.applyInvariants();
        applyStatus(bill, isBlank(bill.getEwayBillNumber()) ? EwayBillStatus.PART_A_PENDING : EwayBillStatus.GENERATED);
        EwayBill saved;
        try {
            saved = repository.save(bill);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "No such shipment, or its E-Way Bill number is already used by this company.");
        }
        log.info("E-Way Bill {} ({}) created for shipment {} in company {} by {}",
                saved.getEwayBillNumber(), saved.getId(), command.shipmentId(), companyId, currentActor());
        auditService.record(AuditAction.EWAY_BILL_CREATED, ENTITY, saved.getId(),
                Map.of("shipmentId", command.shipmentId().toString()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill update(UUID id, UpdateEwayBillCommand command) {
        UUID companyId = requireCompany();
        EwayBill bill = loadOrThrow(id, companyId);
        if (bill.getStatus() == EwayBillStatus.CANCELLED) {
            throw new BusinessRuleException("E-Way Bill %s is cancelled and cannot be edited."
                    .formatted(id));
        }
        requireCurrentVersion(bill, command.expectedVersion());
        applyData(bill, command.data());
        bill.applyInvariants();
        EwayBill saved = repository.save(bill);
        log.info("E-Way Bill {} ({}) updated in company {} by {}", saved.getEwayBillNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.EWAY_BILL_UPDATED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public EwayBill getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<EwayBill> search(UUID shipmentId, EwayBillStatus status, Pageable pageable) {
        return repository.search(requireCompany(), shipmentId, status, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill retry(UUID id) {
        UUID companyId = requireCompany();
        EwayBill bill = loadOrThrow(id, companyId);
        if (bill.getStatus() != EwayBillStatus.FAILED && bill.getStatus() != EwayBillStatus.EXPIRED) {
            throw new BusinessRuleException(
                    "E-Way Bill %s is not FAILED/EXPIRED, so there is nothing to retry.".formatted(id));
        }

        boolean retryPartB = bill.getStatus() == EwayBillStatus.FAILED && bill.partAGenerated();
        EwayBill saved = retryPartB ? retryPartB(bill) : retryPartA(bill);

        log.info("E-Way Bill {} ({}) retried -> {} in company {} by {}", saved.getEwayBillNumber(),
                saved.getId(), saved.getStatus(), companyId, currentActor());
        auditService.record(AuditAction.EWAY_BILL_RETRIED, ENTITY, saved.getId(),
                Map.of("status", saved.getStatus().name()));
        return saved;
    }

    private EwayBill retryPartA(EwayBill bill) {
        applyStatus(bill, EwayBillStatus.PART_A_PENDING);
        try {
            EwayBillProvider.PartAResult result = provider.generatePartA(toPartARequest(bill));
            if (result == null || !result.success()) {
                throw new BusinessRuleException("E-Way Bill Part-A retry failed: "
                        + (result == null ? "no response from the provider" : result.failureReason()));
            }
            bill.setEwayBillNumber(result.ewayBillNumber());
            bill.setValidFrom(result.validFrom());
            bill.setValidUntil(result.validUntil());
            bill.setProviderName(result.providerName());
            bill.setProviderReference(result.providerReference());
            bill.setPartAGeneratedAt(Instant.now());
            bill.setLastError(null);
            applyStatus(bill, EwayBillStatus.PART_A_GENERATED);
            return repository.save(bill);
        } catch (Exception e) {
            bill.setLastError(e instanceof BusinessRuleException ? e.getMessage() : safeMessage(e));
            bill.setRetryCount(bill.getRetryCount() + 1);
            applyStatus(bill, EwayBillStatus.FAILED);
            repository.save(bill);
            throw e instanceof BusinessRuleException bre ? bre
                    : new BusinessRuleException("E-Way Bill Part-A retry failed: " + safeMessage(e));
        }
    }

    private EwayBill retryPartB(EwayBill bill) {
        applyStatus(bill, EwayBillStatus.PART_B_PENDING);
        try {
            EwayBillProvider.PartBResult result = provider.updatePartB(new EwayBillProvider.PartBRequest(
                    bill.getEwayBillNumber(), bill.getVehicleNumber(), bill.getTransporterId(), bill.getTransportMode()));
            if (result == null || !result.success()) {
                throw new BusinessRuleException("E-Way Bill Part-B retry failed: "
                        + (result == null ? "no response from the provider" : result.failureReason()));
            }
            if (result.providerReference() != null) {
                bill.setProviderReference(result.providerReference());
            }
            bill.setPartBGeneratedAt(Instant.now());
            bill.setLastError(null);
            applyStatus(bill, EwayBillStatus.GENERATED);
            return repository.save(bill);
        } catch (Exception e) {
            bill.setLastError(e instanceof BusinessRuleException ? e.getMessage() : safeMessage(e));
            bill.setRetryCount(bill.getRetryCount() + 1);
            applyStatus(bill, EwayBillStatus.FAILED);
            repository.save(bill);
            throw e instanceof BusinessRuleException bre ? bre
                    : new BusinessRuleException("E-Way Bill Part-B retry failed: " + safeMessage(e));
        }
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public String upload(UUID id, UploadCommand command) {
        UUID companyId = requireCompany();
        EwayBill bill = loadOrThrow(id, companyId);
        if (bill.getStatus() == EwayBillStatus.CANCELLED) {
            throw new BusinessRuleException("E-Way Bill %s is cancelled and cannot take a new document."
                    .formatted(id));
        }
        String extension = extensionOf(command.filename());
        if (!DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, JPG or PNG are accepted for an E-Way Bill document.");
        }

        String key = "%s/%s/eway-bill-%s.%s".formatted(companyId, bill.getShipmentId(),
                UUID.randomUUID(), extension);
        FileStoragePort.StoredFile stored = fileStoragePort.upload(new FileStoragePort.UploadRequest(
                command.content(), key, command.contentType(), "eway-bill"));
        bill.setDocumentUrl(stored.url());
        repository.save(bill);

        auditService.record(AuditAction.EWAY_BILL_UPLOADED, ENTITY, bill.getId(),
                Map.of("shipmentId", bill.getShipmentId().toString()));
        return stored.url();
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill cancel(UUID id, String remarks) {
        UUID companyId = requireCompany();
        EwayBill bill = loadOrThrow(id, companyId);
        if (bill.getStatus() == EwayBillStatus.CANCELLED) {
            throw new BusinessRuleException("E-Way Bill %s is already cancelled.".formatted(id));
        }
        if (bill.partAGenerated()) {
            EwayBillProvider.CancelResult result;
            try {
                result = provider.cancel(bill.getEwayBillNumber(), remarks);
            } catch (Exception e) {
                throw new BusinessRuleException("Could not cancel the E-Way Bill with the provider: "
                        + safeMessage(e));
            }
            if (result == null || !result.success()) {
                throw new BusinessRuleException("E-Way Bill cancellation was refused: "
                        + (result == null ? "no response from the provider" : result.failureReason()));
            }
        }
        bill.transitionTo(EwayBillStatus.CANCELLED);
        if (remarks != null && !remarks.isBlank()) {
            bill.setRemarks(remarks.trim());
        }
        EwayBill saved = repository.save(bill);
        log.info("E-Way Bill {} ({}) cancelled in company {} by {}", saved.getEwayBillNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.EWAY_BILL_CANCELLED, ENTITY, saved.getId(), Map.of());
        return saved;
    }

    // ------------------------------------------------------------------------ helpers

    private void applyData(EwayBill bill, EwayBillDataCommand data) {
        // ewayBillNumber/transporterId/vehicleNumber/distance/validFrom/validUntil are
        // only ever meaningful on the manual-attach path (create/update/upsertForShipment)
        // — the auto-generation path (generatePartAForShipment) never reads them back off
        // the command, only what the provider itself returns.
        bill.setEwayBillNumber(data.ewayBillNumber());
        bill.setInvoiceNumber(data.invoiceNumber());
        bill.setInvoiceDate(data.invoiceDate());
        bill.setInvoiceValue(data.invoiceValue());
        bill.setDocumentType(parseDocumentType(data.documentType()));
        bill.setDocumentNumber(data.documentNumber());
        bill.setDocumentDate(data.documentDate());
        bill.setTransporterId(data.transporterId());
        bill.setVehicleNumber(data.vehicleNumber());
        bill.setDistance(data.distance());
        bill.setValidFrom(data.validFrom());
        bill.setValidUntil(data.validUntil());
        bill.setConsignorGstin(data.consignorGstin());
        bill.setConsigneeGstin(data.consigneeGstin());
        if (data.documentUrl() != null && !data.documentUrl().isBlank()) {
            bill.setDocumentUrl(data.documentUrl().trim());
        }
        if (data.remarks() != null) {
            bill.setRemarks(data.remarks());
        }
    }

    private void applyContext(EwayBill bill, ShipmentEwayBillContext ctx) {
        if (ctx == null) {
            return;
        }
        bill.setConsignorName(ctx.consignorName());
        bill.setConsignorAddress(ctx.consignorAddress());
        bill.setConsignorPincode(ctx.consignorPincode());
        bill.setConsigneeName(ctx.consigneeName());
        bill.setConsigneeAddress(ctx.consigneeAddress());
        bill.setConsigneePincode(ctx.consigneePincode());
        bill.setProductDescription(ctx.productDescription());
    }

    private EwayBillDocumentType parseDocumentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EwayBillDocumentType.INVOICE;
        }
        try {
            return EwayBillDocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("No such E-Way Bill document type: " + raw);
        }
    }

    /** Builds the provider request from the row itself (not the incoming command) so
     *  {@link #retry} can rebuild the exact same request without any fresh input —
     *  everything Part-A needs is already snapshotted on the entity. */
    private EwayBillProvider.PartARequest toPartARequest(EwayBill bill) {
        return new EwayBillProvider.PartARequest(bill.getInvoiceNumber(), bill.getInvoiceDate(),
                bill.getInvoiceValue(), bill.getDocumentType() == null ? null : bill.getDocumentType().name(),
                bill.getDocumentNumber(), bill.getDocumentDate(),
                bill.getConsignorGstin(), bill.getConsignorName(), bill.getConsignorAddress(), bill.getConsignorPincode(),
                bill.getConsigneeGstin(), bill.getConsigneeName(), bill.getConsigneeAddress(), bill.getConsigneePincode(),
                bill.getProductDescription());
    }

    /** Only calls {@link EwayBill#transitionTo} when the status is actually changing —
     *  re-processing a row already at the target status must not throw just because
     *  {@code EwayBillStatus.canTransitionTo} refuses a self-loop. */
    private void applyStatus(EwayBill bill, EwayBillStatus next) {
        if (bill.getStatus() != next) {
            bill.transitionTo(next);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Never surfaces a raw exception message from an HTTP client to a user-visible
     *  field — those can echo request content (headers, body) that must never be logged
     *  or stored. Callers that need a user-facing reason use the provider's own
     *  {@code failureReason} instead; this is only for the "provider threw" catch-all. */
    private static String safeMessage(Exception e) {
        return e.getClass().getSimpleName();
    }

    private EwayBill loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireCurrentVersion(EwayBill bill, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(bill.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(EwayBill.class, bill.getId());
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. E-Way Bills belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
