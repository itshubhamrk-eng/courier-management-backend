package com.courier.modules.shipment.application;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.PackageType;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.domain.Route;
import com.courier.modules.master.domain.ServiceType;
import com.courier.modules.pricing.application.PricingEngine;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.domain.ChargeableWeightCalculator;
import com.courier.modules.pricing.domain.VolumetricCalculator;
import com.courier.modules.pricing.domain.WeightCalculator;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.shipment.application.command.CreateShipmentCommand;
import com.courier.modules.shipment.application.command.ShipmentItemCommand;
import com.courier.modules.shipment.application.command.UpdateShipmentCommand;
import com.courier.modules.shipment.application.event.ShipmentEvent;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignment;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentStatus;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentDocument;
import com.courier.modules.shipment.domain.ShipmentDocumentRepository;
import com.courier.modules.shipment.domain.ShipmentDocumentType;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentItemRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentSpecifications;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.modules.shipment.domain.ShipmentType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shipment Booking use cases. Per-method {@code @PreAuthorize} gates by JWT role tier —
 * see {@link ShipmentService} for the audiences. Company isolation is the project's usual
 * two layers: the Hibernate filter, plus {@code findByIdWithinCompany} on every single-row
 * load, so a foreign shipment id 404s.
 *
 * <p>This class orchestrates; it does not re-decide. Sender/receiver are plain text typed
 * at booking time — no Customer module lookup. Every cross-module id it does still carry
 * (branch, service/package/payment type, route, rate) is validated by the module that owns
 * it — most of that validation happens inside one call to
 * {@link PricingEngine#calculate}, which already runs route, serviceability, rate and
 * weight-slab validation. See the class-level notes on each private helper for exactly
 * which module answers which question.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private static final String ENTITY = "Shipment";

    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.OPERATOR + "')";
    private static final String READERS = "isAuthenticated()";

    /** The YYMM prefix of a tracking number — UTC, so the month never depends on the
     *  server's local timezone. */
    private static final DateTimeFormatter YEAR_MONTH =
            DateTimeFormatter.ofPattern("yyMM").withZone(ZoneOffset.UTC);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository itemRepository;
    private final ShipmentChargeRepository chargeRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final ShipmentDocumentRepository documentRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final BranchShipmentSequenceRepository branchShipmentSequenceRepository;
    private final CompanyShipmentSequenceRepository companyShipmentSequenceRepository;

    private final ServiceTypeService serviceTypeService;
    private final PackageTypeService packageTypeService;
    private final PaymentModeService paymentModeService;
    private final RateService rateService;
    private final RouteService routeService;
    private final PricingEngine pricingEngine;
    private final PricingProperties pricingProperties;
    private final WalletService walletService;
    private final com.courier.modules.company.application.UserService userService;
    private final com.courier.modules.company.application.BranchService branchService;

    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------- create

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment create(CreateShipmentCommand command) {
        UUID companyId = requireCompany();

        List<ShipmentItem> items = buildItems(command.items(), command.actualWeight(),
                command.length(), command.width(), command.height());
        WeightSummary weight = summariseWeight(items);
        requireWithinPackageTypeCeiling(command.packageTypeId(), weight.actualWeight());

        LocalDate bookingDate = command.bookingDate() == null ? LocalDate.now() : command.bookingDate();

        PricingResult priced = priceIt(command.bookingBranchId(), command.deliveryBranchId(),
                command.pickupPincode(), command.deliveryPincode(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId(), weight.chargeableWeight(),
                command.declaredValue(), bookingDate);

        PaymentMode paymentMode = paymentModeService.getById(command.paymentModeId());
        ServiceType serviceType = serviceTypeService.getById(command.serviceTypeId());

        if (paymentMode.isCollectAtBooking()) {
            requireSufficientBalance(command.bookingBranchId(), priced.netAmount());
        }

        Shipment shipment = Shipment.builder()
                .shipmentNumber(nextShipmentNumber(command.bookingBranchId()))
                .trackingNumber(nextTrackingNumber(companyId))
                .bookingDate(bookingDate)
                .bookingBranchId(command.bookingBranchId())
                .deliveryBranchId(command.deliveryBranchId())
                .pickupPincode(command.pickupPincode())
                .deliveryPincode(command.deliveryPincode())
                .senderName(command.senderName())
                .senderAddress(command.senderAddress())
                .senderContact(command.senderContact())
                .receiverName(command.receiverName())
                .receiverAddress(command.receiverAddress())
                .receiverContact(command.receiverContact())
                .serviceTypeId(command.serviceTypeId())
                .packageTypeId(command.packageTypeId())
                .paymentModeId(command.paymentModeId())
                .shipmentType(command.shipmentType() == null ? ShipmentType.NON_DOCUMENT : command.shipmentType())
                .expectedDeliveryDate(expectedDeliveryDate(bookingDate, serviceType))
                .actualWeight(weight.actualWeight())
                .volumetricWeight(weight.volumetricWeight())
                .chargeableWeight(weight.chargeableWeight())
                .declaredValue(command.declaredValue())
                .numberOfPackages(command.numberOfPackages() == null ? 1 : command.numberOfPackages())
                .status(ShipmentStatus.BOOKED)
                .remarks(command.remarks())
                .build();
        shipment.applyInvariants();
        Shipment saved = shipmentRepository.save(shipment);

        persistItems(saved, companyId, items);
        persistCharges(saved, companyId, priced);
        appendHistory(saved, companyId, null, ShipmentStatus.BOOKED, "Shipment booked");

        log.info("Shipment {} ({}) booked in company {} by {}", saved.getShipmentNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.SHIPMENT_BOOKED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(),
                        "trackingNumber", saved.getTrackingNumber(),
                        "netAmount", priced.netAmount().toPlainString()));

        if (paymentMode.isCollectAtBooking()) {
            eventPublisher.publishEvent(new ShipmentEvent.PrepaidBookingConfirmed(
                    saved.getId(), companyId, saved.getBookingBranchId(), saved.getShipmentNumber(),
                    priced.netAmount(), Instant.now()));
        }

        return saved;
    }

    // ------------------------------------------------------------------- update

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment update(UUID id, UpdateShipmentCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(id, companyId);
        requireCurrentVersion(shipment, command.expectedVersion());

        if (!shipment.isEditable()) {
            throw new BusinessRuleException("Shipment %s can only be edited while BOOKED."
                    .formatted(shipment.getShipmentNumber()));
        }

        List<ShipmentItem> items = buildItems(command.items(), command.actualWeight(),
                command.length(), command.width(), command.height());
        WeightSummary weight = summariseWeight(items);
        requireWithinPackageTypeCeiling(command.packageTypeId(), weight.actualWeight());

        LocalDate bookingDate = command.bookingDate() == null ? shipment.getBookingDate() : command.bookingDate();

        PricingResult priced = priceIt(shipment.getBookingBranchId(), command.deliveryBranchId(),
                command.pickupPincode(), command.deliveryPincode(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId(), weight.chargeableWeight(),
                command.declaredValue(), bookingDate);

        ServiceType serviceType = serviceTypeService.getById(command.serviceTypeId());

        shipment.setDeliveryBranchId(command.deliveryBranchId());
        shipment.setPickupPincode(command.pickupPincode());
        shipment.setDeliveryPincode(command.deliveryPincode());
        shipment.setSenderName(command.senderName());
        shipment.setSenderAddress(command.senderAddress());
        shipment.setSenderContact(command.senderContact());
        shipment.setReceiverName(command.receiverName());
        shipment.setReceiverAddress(command.receiverAddress());
        shipment.setReceiverContact(command.receiverContact());
        shipment.setServiceTypeId(command.serviceTypeId());
        shipment.setPackageTypeId(command.packageTypeId());
        shipment.setPaymentModeId(command.paymentModeId());
        shipment.setShipmentType(command.shipmentType());
        shipment.setBookingDate(bookingDate);
        shipment.setExpectedDeliveryDate(expectedDeliveryDate(bookingDate, serviceType));
        shipment.setActualWeight(weight.actualWeight());
        shipment.setVolumetricWeight(weight.volumetricWeight());
        shipment.setChargeableWeight(weight.chargeableWeight());
        shipment.setDeclaredValue(command.declaredValue());
        shipment.setNumberOfPackages(command.numberOfPackages() == null ? 1 : command.numberOfPackages());
        shipment.setRemarks(command.remarks());
        shipment.applyInvariants();
        Shipment saved = shipmentRepository.save(shipment);

        itemRepository.deleteAllByShipmentIdAndCompanyId(saved.getId(), companyId);
        persistItems(saved, companyId, items);
        replaceCharges(saved, companyId, priced);

        log.info("Shipment {} ({}) updated in company {} by {}", saved.getShipmentNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.SHIPMENT_UPDATED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(),
                        "netAmount", priced.netAmount().toPlainString()));

        return saved;
    }

    // --------------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Shipment getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Shipment getByTrackingNumber(String trackingNumber) {
        UUID companyId = requireCompany();
        return shipmentRepository.findByCompanyIdAndTrackingNumber(companyId, trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, trackingNumber));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<Shipment> search(ShipmentCriteria criteria, Pageable pageable) {
        return shipmentRepository.findAll(ShipmentSpecifications.matching(criteria), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Map<UUID, BigDecimal> netAmountsFor(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        return chargeRepository.findByShipmentIdIn(shipmentIds).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, ShipmentCharge::getNetAmount));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<ShipmentItem> getItems(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        return itemRepository.findAllByShipmentIdWithinCompany(shipment.getId(), companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public ShipmentCharges getCharges(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        ShipmentCharge charge = chargeRepository.findByShipmentIdWithinCompany(shipment.getId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("ShipmentCharge", shipmentId));

        return new ShipmentCharges(charge, resolveRouteCode(charge), resolveRateCode(charge));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<ShipmentStatusHistory> getHistory(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        return historyRepository.findAllByShipmentIdWithinCompany(shipment.getId(), companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<ShipmentDocument> getDocuments(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        return documentRepository.findAllByShipmentIdWithinCompany(shipment.getId(), companyId);
    }

    // ------------------------------------------------------------------- cancel

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment cancel(UUID id, String remarks) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(id, companyId);

        if (!shipment.getStatus().isCancellable()) {
            throw new BusinessRuleException(
                    "Shipment %s is %s and can no longer be cancelled — it has left the branch."
                            .formatted(shipment.getShipmentNumber(), shipment.getStatus()));
        }

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.CANCELLED);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.CANCELLED,
                remarks == null || remarks.isBlank() ? "Shipment cancelled" : remarks.trim());

        log.info("Shipment {} ({}) cancelled in company {} by {}", saved.getShipmentNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.SHIPMENT_CANCELLED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(), "previousStatus", previous.name()));

        return saved;
    }

    // ---------------------------------------------------------------- documents

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public ShipmentDocument addDocument(UUID shipmentId, AddDocumentCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        ShipmentDocumentType type = parseDocumentType(command.documentType());

        ShipmentDocument document = ShipmentDocument.builder()
                .shipmentId(shipment.getId())
                .documentType(type)
                .documentName(command.documentName())
                .documentUrl(command.documentUrl())
                .remarks(command.remarks())
                .build();
        document.applyInvariants();
        ShipmentDocument saved = documentRepository.save(document);

        auditService.record(AuditAction.SHIPMENT_DOCUMENT_UPLOADED, ENTITY, shipment.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber(), "documentType", type.name()));

        return saved;
    }

    private ShipmentDocumentType parseDocumentType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("A document type is required.");
        }
        try {
            return ShipmentDocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("No such document type: " + raw);
        }
    }

    // ============================================================= Shipment Movement (V19)

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment attachToManifest(UUID shipmentId, UUID manifestId, UUID expectedBookingBranchId,
                                     UUID expectedDeliveryBranchId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        if (shipment.getStatus() != ShipmentStatus.BOOKED) {
            throw new BusinessRuleException("Shipment %s is %s — only BOOKED shipments can be "
                    .formatted(shipment.getShipmentNumber(), shipment.getStatus())
                    + "added to a manifest.");
        }
        if (!Objects.equals(shipment.getBookingBranchId(), expectedBookingBranchId)
                || !Objects.equals(shipment.getDeliveryBranchId(), expectedDeliveryBranchId)) {
            throw new BusinessRuleException(
                    "Shipment %s travels a different lane than this manifest.".formatted(
                            shipment.getShipmentNumber()));
        }

        ShipmentStatus previous = shipment.getStatus();
        shipment.setManifestId(manifestId);
        shipment.transitionTo(ShipmentStatus.MANIFEST_CREATED);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.MANIFEST_CREATED,
                "Added to manifest", shipment.getBookingBranchId(), manifestId, null);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<Shipment> findManifestCreatedShipments(UUID manifestId) {
        ShipmentCriteria criteria = new ShipmentCriteria(
                java.util.Set.of(ShipmentStatus.MANIFEST_CREATED), null, null, manifestId, null, null, null);
        return shipmentRepository.findAll(ShipmentSpecifications.matching(criteria));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public List<Shipment> transitionToDispatched(List<UUID> shipmentIds, UUID manifestId, UUID vehicleId,
                                                 UUID bookingBranchId) {
        UUID companyId = requireCompany();
        List<Shipment> shipments = shipmentRepository.findAllByCompanyIdAndIdIn(companyId, shipmentIds);
        List<Shipment> saved = new ArrayList<>(shipments.size());
        for (Shipment shipment : shipments) {
            ShipmentStatus previous = shipment.getStatus();
            shipment.transitionTo(ShipmentStatus.DISPATCHED);
            Shipment s = shipmentRepository.save(shipment);
            appendHistory(s, companyId, previous, ShipmentStatus.DISPATCHED, "Manifest dispatched",
                    bookingBranchId, manifestId, vehicleId);
            saved.add(s);
        }
        auditService.record(AuditAction.MANIFEST_DISPATCHED, "Manifest", manifestId,
                Map.of("shipmentCount", saved.size(), "vehicleId", vehicleId.toString()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public BulkMovementResult inScan(UUID receivingBranchId, List<String> trackingNumbers) {
        UUID companyId = requireCompany();
        List<MovementOutcome> outcomes = new ArrayList<>();
        for (String trackingNumber : trackingNumbers) {
            outcomes.add(scanOneIn(companyId, receivingBranchId, trackingNumber));
        }
        return new BulkMovementResult(outcomes);
    }

    private MovementOutcome scanOneIn(UUID companyId, UUID receivingBranchId, String trackingNumber) {
        Optional<Shipment> found = shipmentRepository.findByCompanyIdAndTrackingNumber(companyId, trackingNumber);
        if (found.isEmpty()) {
            return new MovementOutcome(trackingNumber, false, "No such tracking number.");
        }
        Shipment shipment = found.get();
        if (shipment.getStatus() != ShipmentStatus.DISPATCHED) {
            return new MovementOutcome(trackingNumber, false,
                    "Cannot be received — currently " + shipment.getStatus() + ".");
        }
        if (!Objects.equals(shipment.getDeliveryBranchId(), receivingBranchId)) {
            return new MovementOutcome(trackingNumber, false,
                    "Receiving branch does not match this shipment's delivery branch.");
        }

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.IN_SCAN);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.IN_SCAN, "In scan",
                receivingBranchId, saved.getManifestId(), null);
        auditService.record(AuditAction.SHIPMENT_IN_SCANNED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber()));
        return new MovementOutcome(trackingNumber, true, null);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public BulkMovementResult assignOutForDelivery(Collection<UUID> shipmentIds, UUID deliveryUserId) {
        UUID companyId = requireCompany();
        userService.getById(deliveryUserId); // 404s if foreign/missing — no role restriction, see module doc

        List<MovementOutcome> outcomes = new ArrayList<>();
        for (UUID shipmentId : shipmentIds) {
            outcomes.add(assignOneOutForDelivery(companyId, shipmentId, deliveryUserId));
        }
        return new BulkMovementResult(outcomes);
    }

    private MovementOutcome assignOneOutForDelivery(UUID companyId, UUID shipmentId, UUID deliveryUserId) {
        Shipment shipment = shipmentRepository.findByIdWithinCompany(shipmentId, companyId).orElse(null);
        if (shipment == null) {
            return new MovementOutcome(shipmentId.toString(), false, "No such shipment.");
        }
        if (shipment.getStatus() != ShipmentStatus.IN_SCAN) {
            return new MovementOutcome(shipment.getShipmentNumber(), false,
                    "Cannot be assigned — currently " + shipment.getStatus() + ".");
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByShipmentIdWithinCompany(shipmentId, companyId)
                .orElseGet(() -> {
                    DeliveryAssignment fresh = DeliveryAssignment.builder().build();
                    fresh.setCompanyId(companyId);
                    fresh.setShipmentId(shipmentId);
                    return fresh;
                });
        if (assignment.isNew()) {
            assignment.setDeliveryBranchId(shipment.getDeliveryBranchId());
            assignment.setDeliveryUserId(deliveryUserId);
            assignment.setAssignedAt(Instant.now());
        } else {
            assignment.reassign(deliveryUserId, shipment.getDeliveryBranchId());
        }
        deliveryAssignmentRepository.save(assignment);

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.OUT_FOR_DELIVERY);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.OUT_FOR_DELIVERY, "Out for delivery",
                shipment.getDeliveryBranchId(), saved.getManifestId(), null);
        auditService.record(AuditAction.SHIPMENT_OUT_FOR_DELIVERY_ASSIGNED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(), "deliveryUserId", deliveryUserId.toString()));
        return new MovementOutcome(saved.getShipmentNumber(), true, null);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment deliver(UUID shipmentId, DeliverCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException("Shipment %s is %s — only an OUT_FOR_DELIVERY shipment "
                    .formatted(shipment.getShipmentNumber(), shipment.getStatus())
                    + "can be delivered.");
        }
        DeliveryAssignment assignment = deliveryAssignmentRepository
                .findByShipmentIdWithinCompany(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryAssignment", shipmentId));
        assignment.markDelivered(command.receiverName(), command.remarks(), command.otp(),
                command.signatureUrl(), command.photoUrl());
        deliveryAssignmentRepository.save(assignment);

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.DELIVERED);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.DELIVERED,
                command.remarks() == null || command.remarks().isBlank()
                        ? "Delivered to " + command.receiverName() : command.remarks().trim(),
                shipment.getDeliveryBranchId(), saved.getManifestId(), null);
        auditService.record(AuditAction.SHIPMENT_DELIVERED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(), "receiverName", command.receiverName()));
        return saved;
    }

    // MANIFEST_CREATED doubles as "Out Scan Created" (V20, on direct request) — one
    // milestone, not two; see ShipmentStatus's own class doc.
    private static final Map<ShipmentStatus, String> TIMELINE_LABELS = Map.of(
            ShipmentStatus.BOOKED, "Booked",
            ShipmentStatus.MANIFEST_CREATED, "Out Scan Created",
            ShipmentStatus.DISPATCHED, "Dispatched",
            ShipmentStatus.IN_SCAN, "Received",
            ShipmentStatus.OUT_FOR_DELIVERY, "Out For Delivery",
            ShipmentStatus.DELIVERED, "Delivered");

    private static final List<ShipmentStatus> TIMELINE_ORDER = List.of(
            ShipmentStatus.BOOKED, ShipmentStatus.MANIFEST_CREATED,
            ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.DELIVERED);

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<TimelineStep> timeline(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        Map<ShipmentStatus, ShipmentStatusHistory> reached = historyRepository
                .findAllByShipmentIdWithinCompany(shipmentId, companyId).stream()
                .collect(Collectors.toMap(ShipmentStatusHistory::getStatus, h -> h,
                        (first, second) -> second));

        List<TimelineStep> steps = new ArrayList<>(TIMELINE_ORDER.size());
        for (ShipmentStatus status : TIMELINE_ORDER) {
            ShipmentStatusHistory entry = reached.get(status);
            steps.add(new TimelineStep(status, TIMELINE_LABELS.get(status),
                    entry == null ? null : entry.getChangedAt(),
                    entry == null ? null : entry.getChangedBy(),
                    entry != null));
        }
        return steps;
    }

    // ------------------------------------------------------------- cross-module

    /**
     * Route validation, serviceability and rate matching are all the Pricing Engine's own
     * job — this is the one call that covers all three of the brief's "Serviceability
     * Check", "Route Validation" and "Pricing Engine" workflow steps, since {@link
     * PricingEngine#calculate} already performs every one of them internally. Calling
     * {@code RouteService}/{@code RateService} separately first would duplicate logic
     * Pricing already owns — see {@code MEMORY/modules/shipment-booking.md}.
     *
     * <p>{@code actualWeight} passed here is this module's own already-computed
     * <b>chargeable</b> weight, with no dimensions — {@code ChargeableWeightCalculator
     * .calculate(chargeableWeight, 0) == chargeableWeight}, so the Pricing Engine's own
     * internal volumetric/chargeable recomputation is a no-op and the weight it prices
     * against is exactly the one this module already derived from the item grid.
     */
    private PricingResult priceIt(UUID bookingBranchId, UUID deliveryBranchId,
                                  String pickupPincode, String deliveryPincode,
                                  UUID serviceTypeId, UUID packageTypeId, UUID paymentModeId,
                                  BigDecimal chargeableWeight, BigDecimal declaredValue,
                                  LocalDate bookingDate) {
        PricingCommand command = new PricingCommand(bookingBranchId, deliveryBranchId,
                pickupPincode, deliveryPincode, serviceTypeId, packageTypeId, paymentModeId,
                chargeableWeight, null, null, null, declaredValue, bookingDate, null, null);
        return pricingEngine.calculate(command);
    }

    private void requireSufficientBalance(UUID bookingBranchId, BigDecimal required) {
        Wallet wallet = walletService.getForBranch(bookingBranchId);
        if (wallet.getAvailableBalance().compareTo(required) < 0) {
            throw new BusinessRuleException(
                    "Insufficient wallet balance: available %s, required %s."
                            .formatted(wallet.getAvailableBalance().toPlainString(),
                                    required.toPlainString()));
        }
    }

    /**
     * {@code PackageType.maxWeightKg} — "refuse a booking above this, if set" is that
     * master's own documented rule, otherwise unenforced anywhere until a caller actually
     * books against it. Read-only; this module adds no ceiling of its own.
     */
    private void requireWithinPackageTypeCeiling(UUID packageTypeId, BigDecimal actualWeight) {
        PackageType packageType = packageTypeService.getById(packageTypeId);
        BigDecimal ceiling = packageType.getMaxWeightKg();
        if (ceiling != null && actualWeight.compareTo(ceiling) > 0) {
            throw new BusinessRuleException(
                    "Actual weight %s kg exceeds the %s kg maximum for package type %s."
                            .formatted(actualWeight.toPlainString(), ceiling.toPlainString(),
                                    packageType.getCode()));
        }
    }

    private LocalDate expectedDeliveryDate(LocalDate bookingDate, ServiceType serviceType) {
        Integer days = serviceType.getDeliveryDays();
        return days == null ? null : bookingDate.plusDays(days);
    }

    // ------------------------------------------------------------------- weight

    private record WeightSummary(BigDecimal actualWeight, BigDecimal volumetricWeight,
                                 BigDecimal chargeableWeight) {
    }

    private List<ShipmentItem> buildItems(List<ShipmentItemCommand> commands,
                                          BigDecimal fallbackWeight, BigDecimal fallbackLength,
                                          BigDecimal fallbackWidth, BigDecimal fallbackHeight) {
        List<ShipmentItemCommand> effective = (commands == null || commands.isEmpty())
                ? List.of(new ShipmentItemCommand("Package", 1, fallbackWeight, fallbackLength,
                        fallbackWidth, fallbackHeight, null, false, false))
                : commands;

        List<ShipmentItem> items = new ArrayList<>(effective.size());
        for (ShipmentItemCommand c : effective) {
            ShipmentItem item = ShipmentItem.builder()
                    .itemName(c.itemName())
                    .quantity(c.quantity() == null ? 1 : c.quantity())
                    .weight(c.weight())
                    .lengthCm(c.lengthCm())
                    .widthCm(c.widthCm())
                    .heightCm(c.heightCm())
                    .declaredValue(c.declaredValue())
                    .fragile(Boolean.TRUE.equals(c.fragile()))
                    .dangerousGoods(Boolean.TRUE.equals(c.dangerousGoods()))
                    .build();
            item.applyInvariants();
            items.add(item);
        }
        return items;
    }

    /**
     * Sums the item grid using the Pricing Engine's own reusable weight utilities —
     * {@link WeightCalculator#normalise}, {@link VolumetricCalculator#calculate} and
     * {@link ChargeableWeightCalculator#calculate} — rather than reimplementing the
     * volumetric formula here. Each item's own dimensions contribute its own volumetric
     * weight, multiplied by quantity; a shipment with no captured dimensions on any item
     * prices on actual weight alone, the same "not refused" rule
     * {@code VolumetricCalculator} documents for a single parcel.
     */
    private WeightSummary summariseWeight(List<ShipmentItem> items) {
        BigDecimal divisor = pricingProperties.toConfiguration().volumetricDivisor();
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal volumetric = BigDecimal.ZERO;
        for (ShipmentItem item : items) {
            int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
            actual = actual.add(item.getWeight().multiply(BigDecimal.valueOf(quantity)));
            BigDecimal itemVolumetric = VolumetricCalculator.calculate(
                    item.getLengthCm(), item.getWidthCm(), item.getHeightCm(), divisor);
            volumetric = volumetric.add(itemVolumetric.multiply(BigDecimal.valueOf(quantity)));
        }
        BigDecimal normalisedActual = WeightCalculator.normalise(actual);
        BigDecimal normalisedVolumetric = WeightCalculator.normalise(volumetric);
        BigDecimal chargeable = ChargeableWeightCalculator.calculate(normalisedActual, normalisedVolumetric);
        return new WeightSummary(normalisedActual, normalisedVolumetric, chargeable);
    }

    // -------------------------------------------------------------- persistence

    private void persistItems(Shipment shipment, UUID companyId, List<ShipmentItem> items) {
        for (ShipmentItem item : items) {
            item.setCompanyId(companyId);
            item.setShipmentId(shipment.getId());
            itemRepository.save(item);
        }
    }

    private void persistCharges(Shipment shipment, UUID companyId, PricingResult priced) {
        ShipmentCharge charge = toCharge(priced);
        charge.setCompanyId(companyId);
        charge.setShipmentId(shipment.getId());
        chargeRepository.save(charge);
    }

    private void replaceCharges(Shipment shipment, UUID companyId, PricingResult priced) {
        ShipmentCharge existing = chargeRepository
                .findByShipmentIdWithinCompany(shipment.getId(), companyId)
                .orElseGet(() -> {
                    ShipmentCharge fresh = ShipmentCharge.builder().build();
                    fresh.setCompanyId(companyId);
                    fresh.setShipmentId(shipment.getId());
                    return fresh;
                });
        copyCharge(priced, existing);
        chargeRepository.save(existing);
    }

    private ShipmentCharge toCharge(PricingResult priced) {
        ShipmentCharge charge = ShipmentCharge.builder().build();
        copyCharge(priced, charge);
        return charge;
    }

    private void copyCharge(PricingResult priced, ShipmentCharge charge) {
        charge.setFreight(priced.freight());
        charge.setFuelCharge(priced.fuelCharge());
        charge.setHandlingCharge(priced.handlingCharge());
        charge.setOdaCharge(priced.odaCharge());
        charge.setInsuranceCharge(priced.insuranceCharge());
        charge.setGstAmount(priced.gstAmount());
        charge.setDiscountAmount(priced.discountAmount());
        charge.setRoundOff(priced.roundOff());
        charge.setNetAmount(priced.netAmount());
        charge.setMatchedRouteId(priced.matchedRoute() == null ? null : priced.matchedRoute().getId());
        charge.setMatchedRateId(priced.matchedRate() == null ? null : priced.matchedRate().getId());
    }

    private void appendHistory(Shipment shipment, UUID companyId, ShipmentStatus previous,
                               ShipmentStatus status, String remarks) {
        appendHistory(shipment, companyId, previous, status, remarks, null, null, null);
    }

    /**
     * @param branchId   which branch this transition happened at — null for BOOKED/CANCELLED
     * @param manifestId which manifest this transition belongs to — set from MANIFEST_CREATED onward
     * @param vehicleId  which vehicle carried it — set only on the DISPATCHED entry
     */
    private void appendHistory(Shipment shipment, UUID companyId, ShipmentStatus previous,
                               ShipmentStatus status, String remarks, UUID branchId, UUID manifestId,
                               UUID vehicleId) {
        ShipmentStatusHistory entry = ShipmentStatusHistory.builder()
                .shipmentId(shipment.getId())
                .branchId(branchId)
                .manifestId(manifestId)
                .vehicleId(vehicleId)
                .status(status)
                .previousStatus(previous)
                .remarks(remarks)
                .changedBy(SecurityUtils.getCurrentUserId().orElse(null))
                .changedAt(Instant.now())
                .build();
        entry.setCompanyId(companyId);
        historyRepository.save(entry);
    }

    /**
     * Informational only — which lane priced this shipment, for a support desk reading
     * the charge breakup later. Resolved fresh rather than stored, so a route renamed
     * after booking still shows its current code; a route deleted since simply shows
     * none, since {@code matchedRouteId} carries no physical FK (see the migration).
     */
    private String resolveRouteCode(ShipmentCharge charge) {
        if (charge.getMatchedRouteId() == null) {
            return null;
        }
        Route route = tryResolve(() -> routeService.getById(charge.getMatchedRouteId()));
        return route == null ? null : route.getCode();
    }

    private String resolveRateCode(ShipmentCharge charge) {
        if (charge.getMatchedRateId() == null) {
            return null;
        }
        Rate rate = tryResolve(() -> rateService.getById(charge.getMatchedRateId()));
        return rate == null ? null : rate.getRateCode();
    }

    private <T> T tryResolve(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private Shipment loadOrThrow(UUID id, UUID companyId) {
        return shipmentRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * {@code "<BRANCH_CODE>-<6-digit serial>"} — a per-branch counter, not a random
     * candidate: {@link BranchShipmentSequenceRepository#advance} takes the row's own
     * lock, so no existence-check-and-retry is needed, the same as {@link
     * #nextTrackingNumber}.
     */
    private String nextShipmentNumber(UUID bookingBranchId) {
        byte[] branchIdBytes = TimeOrderedUuid.toBytes(bookingBranchId);
        branchShipmentSequenceRepository.advance(branchIdBytes);
        long serial = branchShipmentSequenceRepository.nextValue();
        String branchCode = branchService.getById(bookingBranchId).getBranchCode();
        return "%s-%06d".formatted(branchCode, serial);
    }

    /**
     * {@code "<YYMM><7-digit serial>"}, 11 digits total (e.g. {@code 260800000001}) — a
     * company-wide counter (every branch of a company shares one series), on direct
     * request replacing the old dated-random AWB format. Same collision-free counter
     * contract as {@link #nextShipmentNumber}.
     */
    private String nextTrackingNumber(UUID companyId) {
        byte[] companyIdBytes = TimeOrderedUuid.toBytes(companyId);
        companyShipmentSequenceRepository.advance(companyIdBytes);
        long serial = companyShipmentSequenceRepository.nextValue();
        String yearMonth = YEAR_MONTH.format(Instant.now());
        return "%s%07d".formatted(yearMonth, serial);
    }

    private void requireCurrentVersion(Shipment shipment, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(shipment.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Shipment.class, shipment.getId());
        }
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Shipments belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
