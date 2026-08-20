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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * E-Way Bill Management use cases. See {@link EwayBillService} for the module's own
 * business rule and the two entry points that create/update a row.
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
    public void enforceBookingRequirement(BigDecimal invoiceValue, EwayBillDataCommand ewayBill) {
        if (!isRequired(invoiceValue)) {
            return;
        }
        if (ewayBill == null) {
            throw new BusinessRuleException(mandatoryMessage());
        }
        EwayBillProvider.ValidationOutcome outcome = provider.validate(toValidationRequest(ewayBill));
        if (!outcome.valid()) {
            throw new BusinessRuleException(mandatoryMessage() + " " + outcome.reason());
        }
    }

    /** Exact wording the brief specifies, with the company's own threshold interpolated —
     *  the default (50000.00) renders identically to the brief's own example text. */
    private String mandatoryMessage() {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return "E-Way Bill is mandatory because invoice value exceeds ₹"
                + format.format(mandatoryThreshold()) + ".";
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill upsertForShipment(UUID shipmentId, EwayBillDataCommand ewayBill) {
        if (ewayBill == null) {
            return null;
        }
        UUID companyId = requireCompany();
        // Deliberately not currentFor() here — that helper falls back to a cancelled row
        // for display purposes (findLatestForShipment), but a write must never resurrect
        // one: a cancelled E-Way Bill is reissued as a fresh row, never reused in place.
        EwayBill bill = repository.findAllByShipmentIdWithinCompany(shipmentId, companyId).stream()
                .filter(b -> b.getStatus() != EwayBillStatus.CANCELLED)
                .findFirst().orElse(null);
        if (bill == null) {
            bill = EwayBill.builder().shipmentId(shipmentId).status(EwayBillStatus.PENDING).build();
        }
        applyData(bill, ewayBill);
        bill.applyInvariants();

        EwayBillProvider.ValidationOutcome outcome = provider.validate(toValidationRequest(ewayBill));
        applyStatus(bill, outcome.valid() ? EwayBillStatus.VALIDATED : EwayBillStatus.INVALID);

        EwayBill saved = repository.save(bill);
        log.info("E-Way Bill {} ({}) for shipment {} in company {} -> {}", saved.getEwayBillNumber(),
                saved.getId(), shipmentId, companyId, saved.getStatus());
        auditService.record(saved.getStatus() == EwayBillStatus.VALIDATED
                        ? AuditAction.EWAY_BILL_VALIDATED : AuditAction.EWAY_BILL_CREATED,
                ENTITY, saved.getId(), Map.of("shipmentId", shipmentId.toString(), "status", saved.getStatus().name()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Optional<EwayBillSnapshot> findLatestForShipment(UUID shipmentId) {
        return currentFor(shipmentId, requireCompany()).map(this::toSnapshot);
    }

    /** Newest non-cancelled row, or simply the newest if every row for this shipment has
     *  been cancelled — see the class doc on why more than one row may exist. */
    private Optional<EwayBill> currentFor(UUID shipmentId, UUID companyId) {
        List<EwayBill> all = repository.findAllByShipmentIdWithinCompany(shipmentId, companyId);
        return all.stream().filter(b -> b.getStatus() != EwayBillStatus.CANCELLED).findFirst()
                .or(() -> all.stream().findFirst());
    }

    private EwayBillSnapshot toSnapshot(EwayBill b) {
        return new EwayBillSnapshot(b.getId(), b.getEwayBillNumber(), b.getStatus().name(),
                b.getInvoiceValue(), b.getValidFrom(), b.getValidUntil(), b.getDocumentUrl());
    }

    // ------------------------------------------------------------- standalone lifecycle

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public EwayBill create(CreateEwayBillCommand command) {
        UUID companyId = requireCompany();
        EwayBill bill = EwayBill.builder().shipmentId(command.shipmentId())
                .status(EwayBillStatus.PENDING).build();
        applyData(bill, command.data());
        bill.applyInvariants();
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
    public EwayBill validate(UUID id) {
        UUID companyId = requireCompany();
        EwayBill bill = loadOrThrow(id, companyId);
        if (bill.getStatus() == EwayBillStatus.CANCELLED) {
            throw new BusinessRuleException("E-Way Bill %s is cancelled and cannot be validated."
                    .formatted(id));
        }
        EwayBillProvider.ValidationOutcome outcome = provider.validate(toValidationRequest(bill));
        applyStatus(bill, outcome.valid() ? EwayBillStatus.VALIDATED : EwayBillStatus.INVALID);
        EwayBill saved = repository.save(bill);
        log.info("E-Way Bill {} ({}) validated -> {} in company {} by {}", saved.getEwayBillNumber(),
                saved.getId(), saved.getStatus(), companyId, currentActor());
        auditService.record(AuditAction.EWAY_BILL_VALIDATED, ENTITY, saved.getId(),
                Map.of("status", saved.getStatus().name(),
                        "reason", outcome.reason() == null ? "" : outcome.reason()));
        return saved;
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

        // A document replacing an already-VALIDATED/UPLOADED/EXPIRED row does not silently
        // undo or repeat that state; only a row still awaiting one moves forward.
        Set<EwayBillStatus> promotable = Set.of(EwayBillStatus.NOT_REQUIRED, EwayBillStatus.REQUIRED,
                EwayBillStatus.PENDING, EwayBillStatus.INVALID);
        if (promotable.contains(bill.getStatus())) {
            applyStatus(bill, EwayBillStatus.UPLOADED);
        }
        EwayBill saved = repository.save(bill);

        auditService.record(AuditAction.EWAY_BILL_UPLOADED, ENTITY, saved.getId(),
                Map.of("shipmentId", saved.getShipmentId().toString()));
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
        if (data.documentUrl() != null && !data.documentUrl().isBlank()) {
            bill.setDocumentUrl(data.documentUrl().trim());
        }
        if (data.remarks() != null) {
            bill.setRemarks(data.remarks());
        }
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

    private EwayBillProvider.ValidationRequest toValidationRequest(EwayBillDataCommand data) {
        return new EwayBillProvider.ValidationRequest(data.ewayBillNumber(), data.invoiceNumber(),
                data.invoiceDate(), data.invoiceValue(), data.vehicleNumber(),
                data.validFrom(), data.validUntil());
    }

    private EwayBillProvider.ValidationRequest toValidationRequest(EwayBill bill) {
        return new EwayBillProvider.ValidationRequest(bill.getEwayBillNumber(), bill.getInvoiceNumber(),
                bill.getInvoiceDate(), bill.getInvoiceValue(), bill.getVehicleNumber(),
                bill.getValidFrom(), bill.getValidUntil());
    }

    /** Only calls {@link EwayBill#transitionTo} when the status is actually changing —
     *  re-validating a row that is already {@code VALIDATED} with unchanged data must not
     *  throw just because {@code EwayBillStatus.canTransitionTo} refuses a self-loop. */
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
