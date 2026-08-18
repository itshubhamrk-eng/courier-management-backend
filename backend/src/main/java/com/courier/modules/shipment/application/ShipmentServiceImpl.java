package com.courier.modules.shipment.application;

import com.courier.modules.crossing.application.CrossingService;
import com.courier.modules.customer.application.CustomerService;
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
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyDrsSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignment;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentStatus;
import com.courier.modules.shipment.domain.BranchCommissionSummary;
import com.courier.modules.shipment.domain.BranchPerformanceSummary;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentDocument;
import com.courier.modules.shipment.domain.ShipmentDocumentRepository;
import com.courier.modules.shipment.domain.ShipmentDocumentType;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentItemRepository;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentAssetRepository;
import com.courier.modules.shipment.domain.ShipmentAssetType;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentSpecifications;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import com.courier.modules.shipment.domain.ShipmentSummaryStats;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.modules.shipment.domain.ShipmentType;
import com.courier.modules.support.application.TicketCategoryService;
import com.courier.modules.support.application.TicketService;
import com.courier.modules.support.application.command.CreateTicketCommand;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shipment Booking use cases. Per-method {@code @PreAuthorize} gates by JWT role tier —
 * see {@link ShipmentService} for the audiences. Company isolation is the project's usual
 * two layers: the Hibernate filter, plus {@code findByIdWithinCompany} on every single-row
 * load, so a foreign shipment id 404s.
 *
 * <p>This class orchestrates; it does not re-decide. Sender/receiver are plain text typed
 * at booking time, stored on the shipment row exactly as typed — {@link Shipment} itself
 * still carries no {@code customerId} FK. On {@link #create}, both are separately
 * find-or-created as {@code Customer} master data by exact mobile match
 * ({@link CustomerService#findOrCreateForBooking}), purely so the next booking's
 * name/mobile search surfaces them — that write never affects the shipment's own fields
 * and its failure is not expected (see the method for why it isn't wrapped defensively).
 * Every cross-module id it does still carry
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
    private final CompanyDrsSequenceRepository companyDrsSequenceRepository;

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
    private final CustomerService customerService;
    private final CrossingService crossingService;
    private final TicketService ticketService;
    private final TicketCategoryService ticketCategoryService;

    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStoragePort fileStoragePort;
    private final ShipmentAssetRepository shipmentAssetRepository;

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

        boolean crossing = Boolean.TRUE.equals(command.crossing());
        if (crossing && (command.crossingBranchIds() == null || command.crossingBranchIds().isEmpty())) {
            throw new BusinessRuleException(
                    "Pick at least one branch this shipment is crossing through, or turn Crossing off.");
        }
        UUID firstCrossingBranch = crossing ? command.crossingBranchIds().get(0) : null;

        com.courier.modules.company.domain.Branch bookingBranch = branchService.getById(command.bookingBranchId());

        PricingResult priced = priceIt(command.bookingBranchId(), command.deliveryBranchId(),
                command.pickupPincode(), command.deliveryPincode(), command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId(), weight.chargeableWeight(),
                command.declaredValue(), bookingDate, command.freightFactorOverride());

        PaymentMode paymentMode = paymentModeService.getById(command.paymentModeId());
        ServiceType serviceType = serviceTypeService.getById(command.serviceTypeId());

        BigDecimal otherCharges = command.otherCharges() == null ? BigDecimal.ZERO : command.otherCharges();
        BigDecimal netAmount = netAmountWithOtherCharges(priced, otherCharges, bookingBranch);

        if (paymentMode.isCollectAtBooking()) {
            requireSufficientBalance(command.bookingBranchId(), netAmount);
        }

        Shipment shipment = Shipment.builder()
                .shipmentNumber(nextShipmentNumber(command.bookingBranchId(), bookingBranch.getBranchCode()))
                .trackingNumber(nextTrackingNumber(companyId))
                .bookingDate(bookingDate)
                .bookingBranchId(command.bookingBranchId())
                .deliveryBranchId(command.deliveryBranchId())
                .currentLocationId(command.bookingBranchId())
                .nextLocationId(crossing ? firstCrossingBranch : command.deliveryBranchId())
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

        // Reuse-or-create master data for next time's search suggestion; not a shipment
        // field, so a bad name/mobile here fails the whole booking same as any other
        // write in this transaction — no separate try/catch carve-out for it.
        customerService.findOrCreateForBooking(command.senderName(), command.senderContact());
        customerService.findOrCreateForBooking(command.receiverName(), command.receiverContact());

        persistItems(saved, companyId, items);
        persistCharges(saved, companyId, priced, otherCharges, bookingBranch);
        appendHistory(saved, companyId, null, ShipmentStatus.BOOKED, "Shipment booked");

        if (crossing) {
            crossingService.createLegs(saved.getId(), command.crossingBranchIds(), command.crossingCharge());
        }

        log.info("Shipment {} ({}) booked in company {} by {}", saved.getShipmentNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.SHIPMENT_BOOKED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(),
                        "trackingNumber", saved.getTrackingNumber(),
                        "netAmount", netAmount.toPlainString()));

        if (paymentMode.isCollectAtBooking()) {
            // Commission itself is credited later, on Trip Challan creation — see
            // ShipmentEvent.DispatchCommissionEarned, published from transitionToDispatched.
            eventPublisher.publishEvent(new ShipmentEvent.PrepaidBookingConfirmed(
                    saved.getId(), companyId, saved.getBookingBranchId(), saved.getShipmentNumber(),
                    netAmount, Instant.now()));
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
                command.declaredValue(), bookingDate, command.freightFactorOverride());

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

        BigDecimal otherCharges = command.otherCharges() == null ? BigDecimal.ZERO : command.otherCharges();
        com.courier.modules.company.domain.Branch bookingBranch =
                branchService.getById(shipment.getBookingBranchId());

        itemRepository.deleteAllByShipmentIdAndCompanyId(saved.getId(), companyId);
        persistItems(saved, companyId, items);
        replaceCharges(saved, companyId, priced, otherCharges, bookingBranch);

        log.info("Shipment {} ({}) updated in company {} by {}", saved.getShipmentNumber(),
                saved.getId(), companyId, currentActor());
        auditService.record(AuditAction.SHIPMENT_UPDATED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(),
                        "netAmount", netAmountWithOtherCharges(priced, otherCharges, bookingBranch).toPlainString()));

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
    public DeliveryAssignment getDeliveryAssignment(UUID shipmentId) {
        return deliveryAssignmentRepository
                .findByShipmentIdWithinCompany(shipmentId, requireCompany())
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<Shipment> search(ShipmentCriteria criteria, Pageable pageable) {
        return shipmentRepository.findAll(buildSpecification(criteria), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public ShipmentSummaryStats summaryStats(ShipmentCriteria criteria) {
        // Report summary row: aggregates over every matching shipment, not just the current
        // page — unpaged on purpose. This module's data volumes are a company's own shipment
        // history, not a platform-wide table, so an in-memory reduce (matching the existing
        // netAmountsFor/deliveredAtFor "batch by id" style) is simpler than hand-written JPQL
        // aggregate queries for what stays a modest row count.
        List<Shipment> matches = shipmentRepository.findAll(buildSpecification(criteria));
        List<UUID> ids = matches.stream().map(Shipment::getId).toList();
        BigDecimal totalAmount = netAmountsFor(ids).values().stream()
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeight = matches.stream().map(Shipment::getChargeableWeight)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ShipmentStatus, Long> statusCounts = matches.stream()
                .collect(Collectors.groupingBy(Shipment::getStatus, Collectors.counting()));
        return new ShipmentSummaryStats(matches.size(), totalWeight, totalAmount, statusCounts);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<BranchCommissionSummary> commissionSummary(ShipmentCriteria criteria) {
        List<Shipment> matches = shipmentRepository.findAll(buildSpecification(criteria));
        Map<UUID, ShipmentCharge> charges = chargesFor(matches.stream().map(Shipment::getId).toList());
        return matches.stream()
                .filter(s -> charges.containsKey(s.getId()))
                .collect(Collectors.groupingBy(Shipment::getBookingBranchId))
                .entrySet().stream()
                .map(e -> {
                    List<ShipmentCharge> branchCharges = e.getValue().stream()
                            .map(s -> charges.get(s.getId())).toList();
                    return new BranchCommissionSummary(
                            e.getKey(),
                            branchCharges.size(),
                            sum(branchCharges, ShipmentCharge::getNetAmount),
                            sum(branchCharges, ShipmentCharge::getCommissionOnBasicFreight),
                            sum(branchCharges, ShipmentCharge::getBranchCommissionOnOtherAmount),
                            sum(branchCharges, ShipmentCharge::getCompanyCommissionOnBasicFreight),
                            sum(branchCharges, ShipmentCharge::getTotalCommission));
                })
                .sorted(Comparator.comparing(BranchCommissionSummary::totalCommission).reversed())
                .toList();
    }

    private static BigDecimal sum(List<ShipmentCharge> charges, java.util.function.Function<ShipmentCharge, BigDecimal> field) {
        return charges.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<BranchPerformanceSummary> branchPerformance(ShipmentCriteria criteria) {
        List<Shipment> matches = shipmentRepository.findAll(buildSpecification(criteria));
        Map<UUID, BigDecimal> netAmounts = netAmountsFor(matches.stream().map(Shipment::getId).toList());
        return matches.stream()
                .collect(Collectors.groupingBy(Shipment::getBookingBranchId))
                .entrySet().stream()
                .map(e -> {
                    List<Shipment> branchShipments = e.getValue();
                    Map<ShipmentStatus, Long> statusCounts = branchShipments.stream()
                            .collect(Collectors.groupingBy(Shipment::getStatus, Collectors.counting()));
                    long delivered = statusCounts.getOrDefault(ShipmentStatus.DELIVERED, 0L);
                    long returned = statusCounts.getOrDefault(ShipmentStatus.RETURNED, 0L);
                    long cancelled = statusCounts.getOrDefault(ShipmentStatus.CANCELLED, 0L);
                    long total = branchShipments.size();
                    BigDecimal weight = branchShipments.stream().map(Shipment::getChargeableWeight)
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal amount = branchShipments.stream()
                            .map(s -> netAmounts.get(s.getId()))
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new BranchPerformanceSummary(e.getKey(), total, delivered,
                            total - delivered - returned - cancelled, returned, cancelled, weight, amount);
                })
                .sorted(Comparator.comparing(BranchPerformanceSummary::shipmentCount).reversed())
                .toList();
    }

    /** Shared by {@link #search} and {@link #summaryStats} — see the class-level note on
     *  {@code ShipmentCriteria.deliveredDateFrom}/{@code deliveredDateTo} for why this can't
     *  live entirely inside {@link ShipmentSpecifications}. */
    private Specification<Shipment> buildSpecification(ShipmentCriteria criteria) {
        ShipmentCriteria safe = criteria == null ? ShipmentCriteria.none() : criteria;
        var spec = ShipmentSpecifications.matching(safe);
        if (safe.deliveredDateFrom() != null || safe.deliveredDateTo() != null) {
            Instant from = safe.deliveredDateFrom() != null
                    ? safe.deliveredDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
            Instant to = safe.deliveredDateTo() != null
                    ? safe.deliveredDateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.now();
            List<UUID> deliveredIds = deliveryAssignmentRepository.findShipmentIdsDeliveredBetween(from, to);
            spec = spec.and((root, query, cb) -> deliveredIds.isEmpty()
                    ? cb.disjunction() : root.get("id").in(deliveredIds));
        }
        return spec;
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
    public Map<UUID, ShipmentCharge> chargesFor(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        return chargeRepository.findByShipmentIdIn(shipmentIds).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, c -> c));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Map<UUID, Instant> deliveredAtFor(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        return deliveryAssignmentRepository.findByShipmentIdIn(shipmentIds).stream()
                .filter(a -> a.getDeliveredAt() != null)
                .collect(Collectors.toMap(DeliveryAssignment::getShipmentId, DeliveryAssignment::getDeliveredAt));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<Shipment> findByManifestIds(Collection<UUID> manifestIds) {
        if (manifestIds.isEmpty()) return List.of();
        return shipmentRepository.findAllByCompanyIdAndManifestIdIn(requireCompany(), manifestIds);
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

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<ShipmentAsset> getAssets(UUID shipmentId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);
        return shipmentAssetRepository.findAllByShipmentIdWithinCompany(shipment.getId(), companyId);
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

        if (shipment.getStatus() != ShipmentStatus.BOOKED && shipment.getStatus() != ShipmentStatus.READY_FOR_MANIFEST) {
            throw new BusinessRuleException("Shipment %s is %s — only BOOKED or READY_FOR_MANIFEST shipments can be "
                    .formatted(shipment.getShipmentNumber(), shipment.getStatus())
                    + "added to a manifest.");
        }
        // Compared against where the shipment actually is / is headed next, not its fixed
        // booking/delivery branch — the same value for a single-leg shipment (current ==
        // booking, next == delivery), but different once a crossing shipment has advanced
        // past its first hop (see ShipmentServiceImpl.scanOneIn). Pre-V37 rows fall back to
        // the fixed branches, since they never got current/nextLocationId written.
        UUID actualPosition = shipment.getCurrentLocationId() != null
                ? shipment.getCurrentLocationId() : shipment.getBookingBranchId();
        UUID actualNextStop = shipment.getNextLocationId() != null
                ? shipment.getNextLocationId() : shipment.getDeliveryBranchId();
        if (!Objects.equals(actualPosition, expectedBookingBranchId)
                || !Objects.equals(actualNextStop, expectedDeliveryBranchId)) {
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
    @Transactional
    @PreAuthorize(WRITERS)
    public Shipment detachFromManifest(UUID shipmentId, UUID manifestId) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        if (shipment.getStatus() != ShipmentStatus.MANIFEST_CREATED
                || !Objects.equals(shipment.getManifestId(), manifestId)) {
            throw new BusinessRuleException("Shipment %s is not on this manifest.".formatted(
                    shipment.getShipmentNumber()));
        }

        ShipmentStatus previous = shipment.getStatus();
        // A shipment still sitting at its own booking branch reverts to BOOKED, same as
        // ever. One that has already advanced past a crossing hop (its current location is
        // no longer where it was booked) reverts to READY_FOR_MANIFEST instead, so it stays
        // eligible for a future leg from wherever it physically is — not BOOKED, which would
        // let it be re-attached to a manifest starting from its original booking branch.
        boolean atOriginalBookingBranch = shipment.getCurrentLocationId() == null
                || Objects.equals(shipment.getCurrentLocationId(), shipment.getBookingBranchId());
        ShipmentStatus revertTo = atOriginalBookingBranch ? ShipmentStatus.BOOKED : ShipmentStatus.READY_FOR_MANIFEST;
        shipment.setManifestId(null);
        shipment.transitionTo(revertTo);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, revertTo, "Removed from manifest");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<Shipment> findManifestCreatedShipments(UUID manifestId) {
        ShipmentCriteria criteria = new ShipmentCriteria(
                java.util.Set.of(ShipmentStatus.MANIFEST_CREATED), null, null, null, null, manifestId,
                null, null, null, null, null);
        return shipmentRepository.findAll(ShipmentSpecifications.matching(criteria));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public List<Shipment> transitionToDispatched(List<UUID> shipmentIds, UUID manifestId, UUID vehicleId,
                                                 UUID bookingBranchId) {
        UUID companyId = requireCompany();
        List<Shipment> shipments = shipmentRepository.findAllByCompanyIdAndIdIn(companyId, shipmentIds);
        Map<UUID, ShipmentCharge> charges = chargesFor(shipmentIds);
        List<Shipment> saved = new ArrayList<>(shipments.size());
        for (Shipment shipment : shipments) {
            ShipmentStatus previous = shipment.getStatus();
            shipment.transitionTo(ShipmentStatus.DISPATCHED);
            Shipment s = shipmentRepository.save(shipment);
            appendHistory(s, companyId, previous, ShipmentStatus.DISPATCHED, "Manifest dispatched",
                    bookingBranchId, manifestId, vehicleId);
            saved.add(s);
            publishDispatchCommissionIfEarned(s, companyId, charges.get(s.getId()));
        }
        auditService.record(AuditAction.MANIFEST_DISPATCHED, "Manifest", manifestId,
                Map.of("shipmentCount", saved.size(), "vehicleId", vehicleId.toString()));
        return saved;
    }

    // Branch commission is credited here, on Trip Challan (manifest dispatch) creation —
    // not at booking time. Same condition booking used to gate the credit on: the payment
    // mode collects at booking, and the booking branch itself has instantCommission on.
    private void publishDispatchCommissionIfEarned(Shipment shipment, UUID companyId, ShipmentCharge charge) {
        if (charge == null) {
            return;
        }
        PaymentMode paymentMode = paymentModeService.getById(shipment.getPaymentModeId());
        if (!paymentMode.isCollectAtBooking()) {
            return;
        }
        com.courier.modules.company.domain.Branch bookingBranch =
                branchService.getById(shipment.getBookingBranchId());
        if (!bookingBranch.isInstantCommission()) {
            return;
        }
        // Only the branch's own two lines — totalCommission (V28) also folds in the
        // company's own commissionOnBasicFreight, which is company revenue and must
        // never land in the branch's wallet.
        BigDecimal branchCommission = charge.getCommissionOnBasicFreight()
                .add(charge.getBranchCommissionOnOtherAmount());
        if (branchCommission.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        eventPublisher.publishEvent(new ShipmentEvent.DispatchCommissionEarned(
                shipment.getId(), companyId, shipment.getBookingBranchId(), shipment.getShipmentNumber(),
                branchCommission, Instant.now()));
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public BulkMovementResult inScan(UUID receivingBranchId, List<String> trackingNumbers, String manifestNumber,
                                      List<String> missingTrackingNumbers) {
        UUID companyId = requireCompany();
        List<MovementOutcome> outcomes = new ArrayList<>();
        for (String trackingNumber : trackingNumbers) {
            outcomes.add(scanOneIn(companyId, receivingBranchId, trackingNumber));
        }
        String shortageTicketNumber = (missingTrackingNumbers == null || missingTrackingNumbers.isEmpty())
                ? null
                : raiseShortageTicket(receivingBranchId, manifestNumber, missingTrackingNumbers);
        return new BulkMovementResult(outcomes, null, shortageTicketNumber);
    }

    /**
     * The operator's own explicit "not physically on this THC" checklist answer — see
     * {@link ShipmentService#inScan}'s own doc. Never blocks or rolls back the in-scan
     * itself: a missing "Shipment Issue" category (SUPER_ADMIN deactivated/renamed it) just
     * skips the ticket with a warning, same graceful-degradation precedent every other
     * best-effort side effect in this module follows.
     */
    private String raiseShortageTicket(UUID receivingBranchId, String manifestNumber,
                                        List<String> missingTrackingNumbers) {
        Optional<TicketCategory> category = ticketCategoryService.listCategories().stream()
                .filter(TicketCategory::isActive)
                .filter(c -> "Shipment Issue".equalsIgnoreCase(c.getName()))
                .findFirst();
        if (category.isEmpty()) {
            log.warn("No 'Shipment Issue' ticket category found — skipping auto-raised shortage ticket "
                    + "for {} short-received shipment(s) at branch {}", missingTrackingNumbers.size(), receivingBranchId);
            return null;
        }
        String manifestRef = (manifestNumber == null || manifestNumber.isBlank()) ? "" : " (" + manifestNumber + ")";
        String subject = "%d shipment(s) short-received%s".formatted(missingTrackingNumbers.size(), manifestRef);
        String description = ("The following %d shipment(s) were left unchecked as \"not physically available\" "
                + "during In Scan%s and are still showing DISPATCHED: %s.")
                .formatted(missingTrackingNumbers.size(), manifestRef, String.join(", ", missingTrackingNumbers));
        Ticket ticket = ticketService.create(new CreateTicketCommand(
                subject, description, category.get().getId(), null, TicketPriority.HIGH,
                null, null, receivingBranchId, null));
        log.info("Auto-raised ticket {} ({}) for {} short-received shipment(s) at branch {}",
                ticket.getTicketNumber(), ticket.getId(), missingTrackingNumbers.size(), receivingBranchId);
        return ticket.getTicketNumber();
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
        // A shipment's next stop is its crossing hop when it has one still ahead, otherwise
        // its own delivery branch — the same value for a non-crossing shipment either way
        // (pre-V37 rows fall back to deliveryBranchId, since they never got nextLocationId
        // written).
        UUID expectedNextStop = shipment.getNextLocationId() != null
                ? shipment.getNextLocationId() : shipment.getDeliveryBranchId();
        if (!Objects.equals(expectedNextStop, receivingBranchId)) {
            return new MovementOutcome(trackingNumber, false,
                    "Receiving branch does not match this shipment's next stop.");
        }

        boolean finalDestination = Objects.equals(shipment.getDeliveryBranchId(), receivingBranchId);
        ShipmentStatus previous = shipment.getStatus();
        shipment.setCurrentLocationId(receivingBranchId);

        if (finalDestination) {
            shipment.transitionTo(ShipmentStatus.IN_SCAN);
            Shipment saved = shipmentRepository.save(shipment);
            appendHistory(saved, companyId, previous, ShipmentStatus.IN_SCAN, "In scan",
                    receivingBranchId, saved.getManifestId(), null);
            auditService.record(AuditAction.SHIPMENT_IN_SCANNED, ENTITY, saved.getId(),
                    Map.of("shipmentNumber", saved.getShipmentNumber()));
            return new MovementOutcome(trackingNumber, true, null);
        }

        // Received at an intermediate crossing hub, not the final branch: this hop of the
        // route is done, and the shipment is ready for its next leg's manifest — not
        // IN_SCAN, which this codebase's out-for-delivery worklist would otherwise treat as
        // "arrived, ready to go out for delivery" at the wrong branch.
        UUID nextHop = crossingService.arriveAt(shipment.getId(), receivingBranchId)
                .orElse(shipment.getDeliveryBranchId());
        shipment.setNextLocationId(nextHop);
        shipment.setManifestId(null);
        shipment.transitionTo(ShipmentStatus.READY_FOR_MANIFEST);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.READY_FOR_MANIFEST,
                "In scan at crossing hub, ready for next leg", receivingBranchId, null, null);
        auditService.record(AuditAction.SHIPMENT_IN_SCANNED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(), "crossingHub", true));
        return new MovementOutcome(trackingNumber, true, null);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public BulkMovementResult assignOutForDelivery(Collection<UUID> shipmentIds, UUID deliveryUserId) {
        UUID companyId = requireCompany();
        userService.getById(deliveryUserId); // 404s if foreign/missing — no role restriction, see module doc

        String drsNumber = nextDrsNumber(companyId);
        List<MovementOutcome> outcomes = new ArrayList<>();
        for (UUID shipmentId : shipmentIds) {
            outcomes.add(assignOneOutForDelivery(companyId, shipmentId, deliveryUserId, drsNumber));
        }
        return new BulkMovementResult(outcomes, drsNumber);
    }

    private MovementOutcome assignOneOutForDelivery(UUID companyId, UUID shipmentId, UUID deliveryUserId,
                                                      String drsNumber) {
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
        assignment.setDrsNumber(drsNumber);
        deliveryAssignmentRepository.save(assignment);

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.OUT_FOR_DELIVERY);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.OUT_FOR_DELIVERY, "DRS",
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
        assignment.markDelivered(command.receiverName(), command.remarks(), command.otp());
        deliveryAssignmentRepository.save(assignment);
        recordAsset(shipmentId, companyId, ShipmentAssetType.POD, "SIGNATURE", command.signatureUrl());
        recordAsset(shipmentId, companyId, ShipmentAssetType.POD, "PHOTO", command.photoUrl());

        ShipmentStatus previous = shipment.getStatus();
        shipment.transitionTo(ShipmentStatus.DELIVERED);
        Shipment saved = shipmentRepository.save(shipment);
        appendHistory(saved, companyId, previous, ShipmentStatus.DELIVERED,
                command.remarks() == null || command.remarks().isBlank()
                        ? "Delivered to " + command.receiverName() : command.remarks().trim(),
                shipment.getDeliveryBranchId(), saved.getManifestId(), null);
        auditService.record(AuditAction.SHIPMENT_DELIVERED, ENTITY, saved.getId(),
                Map.of("shipmentNumber", saved.getShipmentNumber(), "receiverName", command.receiverName()));

        PaymentMode paymentMode = paymentModeService.getById(saved.getPaymentModeId());
        if (paymentMode.isCollectAtDelivery()) {
            ShipmentCharge charge = chargeRepository.findByShipmentIdWithinCompany(saved.getId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("ShipmentCharge", saved.getId()));
            eventPublisher.publishEvent(new ShipmentEvent.CodCollectedAtDelivery(
                    saved.getId(), companyId, saved.getDeliveryBranchId(), saved.getShipmentNumber(),
                    charge.getNetAmount(), Instant.now()));
        }

        int totalQty = itemRepository.findAllByShipmentIdWithinCompany(saved.getId(), companyId).stream()
                .mapToInt(ShipmentItem::getQuantity).sum();
        BigDecimal drsChargePerQty = branchService.getById(saved.getDeliveryBranchId()).getDrsChargePerQty();
        BigDecimal drsCharge = drsChargePerQty.multiply(BigDecimal.valueOf(totalQty));
        if (drsCharge.compareTo(BigDecimal.ZERO) > 0) {
            eventPublisher.publishEvent(new ShipmentEvent.DrsChargeApplicable(
                    saved.getId(), companyId, saved.getDeliveryBranchId(), saved.getShipmentNumber(),
                    drsCharge, Instant.now()));
        }

        return saved;
    }

    /** Persists one asset row if {@code url} is actually present — {@code deliver()} calls
     *  this once for signature and once for photo, either of which is commonly blank. */
    private void recordAsset(UUID shipmentId, UUID companyId, ShipmentAssetType type, String kind, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        shipmentAssetRepository.save(ShipmentAsset.builder()
                .shipmentId(shipmentId)
                .assetType(type)
                .kind(kind)
                .assetUrl(url.trim())
                .build());
    }

    private static final Set<String> BOOKING_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "heic");

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public String uploadShipmentImage(UUID shipmentId, UploadShipmentImageCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        String extension = extensionOf(command.filename());
        if (!BOOKING_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG/PNG/WEBP/HEIC images are accepted for a shipment photo.");
        }

        String key = "%s/%s/photo-%s.%s".formatted(companyId, shipmentId, UUID.randomUUID(), extension);
        FileStoragePort.StoredFile stored = fileStoragePort.upload(new FileStoragePort.UploadRequest(
                command.content(), key, command.contentType(), "shipment-photo"));

        recordAsset(shipmentId, companyId, ShipmentAssetType.BOOKING, "PHOTO", stored.url());

        auditService.record(AuditAction.SHIPMENT_IMAGE_UPLOADED, ENTITY, shipment.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber()));

        return stored.url();
    }

    private static final Set<String> POD_FILE_KINDS = Set.of("PHOTO", "SIGNATURE");

    /** Original-filename extension -> accepted, deliberately not the declared
     *  {@code Content-Type} header: browsers set that inconsistently for some video/HEIC
     *  variants, while the extension the user actually picked is what ends up in the S3
     *  key anyway. */
    private static final Set<String> POD_FILE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "heic", "mp4", "mov", "pdf");

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public String uploadPodFile(UUID shipmentId, UploadPodFileCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = loadOrThrow(shipmentId, companyId);

        String kind = command.kind() == null ? "" : command.kind().trim().toUpperCase();
        if (!POD_FILE_KINDS.contains(kind)) {
            throw new BusinessRuleException("File kind must be one of " + POD_FILE_KINDS + ".");
        }
        String extension = extensionOf(command.filename());
        if (!POD_FILE_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG/PNG/WEBP/HEIC images, MP4/MOV video, or PDF are accepted for "
                            + "proof of delivery.");
        }

        String key = "%s/%s/%s-%s.%s".formatted(companyId, shipmentId, kind.toLowerCase(),
                UUID.randomUUID(), extension);
        FileStoragePort.StoredFile stored = fileStoragePort.upload(new FileStoragePort.UploadRequest(
                command.content(), key, command.contentType(), "pod"));

        auditService.record(AuditAction.SHIPMENT_POD_UPLOADED, ENTITY, shipment.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber(), "kind", kind));

        return stored.url();
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    // MANIFEST_CREATED doubles as "Loading Sheet Created" (V20, on direct request) — one
    // milestone, not two; see ShipmentStatus's own class doc.
    private static final Map<ShipmentStatus, String> TIMELINE_LABELS = Map.of(
            ShipmentStatus.BOOKED, "Booked",
            ShipmentStatus.MANIFEST_CREATED, "Loading Sheet Created",
            ShipmentStatus.DISPATCHED, "Dispatched",
            ShipmentStatus.IN_SCAN, "Received",
            ShipmentStatus.OUT_FOR_DELIVERY, "DRS",
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

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<DrsSummary> listDrs(LocalDate from, LocalDate to, UUID deliveryBranchId) {
        UUID companyId = requireCompany();
        LocalDate rangeFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate rangeTo = to != null ? to : LocalDate.now();
        List<DeliveryAssignment> rows = deliveryAssignmentRepository.findByCompanyAndAssignedAtBetween(
                companyId, rangeFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                rangeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (deliveryBranchId != null) {
            rows = rows.stream().filter(d -> d.getDeliveryBranchId().equals(deliveryBranchId)).toList();
        }

        return rows.stream()
                .collect(Collectors.groupingBy(d -> new DrsGroupKey(d.getDeliveryUserId(),
                        d.getDeliveryBranchId(), d.getAssignedAt().atZone(ZoneOffset.UTC).toLocalDate())))
                .entrySet().stream()
                .map(e -> {
                    long delivered = e.getValue().stream()
                            .filter(d -> d.getStatus() == DeliveryAssignmentStatus.DELIVERED).count();
                    return new DrsSummary(e.getKey().deliveryUserId(), e.getKey().deliveryBranchId(),
                            e.getKey().runDate(), representativeDrsNumber(e.getValue()), e.getValue().size(),
                            (int) delivered, e.getValue().size() - (int) delivered);
                })
                .sorted(Comparator.comparing(DrsSummary::runDate).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public DrsDetail getDrsDetail(UUID deliveryUserId, UUID deliveryBranchId, LocalDate runDate) {
        UUID companyId = requireCompany();
        List<DeliveryAssignment> assignments = deliveryAssignmentRepository.findByCompanyAndAssignedAtBetween(
                companyId, runDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                runDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .stream()
                .filter(d -> d.getDeliveryUserId().equals(deliveryUserId)
                        && d.getDeliveryBranchId().equals(deliveryBranchId))
                .toList();
        if (assignments.isEmpty()) {
            throw new ResourceNotFoundException("DRS", deliveryUserId);
        }

        List<UUID> shipmentIds = assignments.stream().map(DeliveryAssignment::getShipmentId).toList();
        Map<UUID, Shipment> shipments = shipmentRepository.findAllByCompanyIdAndIdIn(companyId, shipmentIds)
                .stream().collect(Collectors.toMap(Shipment::getId, s -> s));
        Map<UUID, BigDecimal> netAmounts = chargeRepository.findByShipmentIdIn(shipmentIds).stream()
                .collect(Collectors.toMap(ShipmentCharge::getShipmentId, ShipmentCharge::getNetAmount));

        List<DrsShipmentRow> rows = assignments.stream()
                .map(a -> {
                    Shipment s = shipments.get(a.getShipmentId());
                    if (s == null) return null;
                    return new DrsShipmentRow(s.getId(), s.getShipmentNumber(), s.getTrackingNumber(),
                            s.getReceiverName(), s.getReceiverContact(), s.getPaymentModeId(),
                            netAmounts.get(s.getId()), s.getStatus(), a.getDeliveredAt());
                })
                .filter(Objects::nonNull)
                .toList();
        return new DrsDetail(deliveryUserId, deliveryBranchId, runDate, representativeDrsNumber(assignments), rows);
    }

    /** {@code assignments} is already ordered newest-first (the repository query's own
     *  {@code order by assignedAt desc}) — the most recently generated number in the group
     *  represents the run; null once no assignment in it carries one (pre-V31 rows only). */
    private String representativeDrsNumber(List<DeliveryAssignment> assignments) {
        return assignments.stream().map(DeliveryAssignment::getDrsNumber)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private record DrsGroupKey(UUID deliveryUserId, UUID deliveryBranchId, LocalDate runDate) {
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
     *
     * <p>Falling back to the company's distance+weight Freight Factor grid when no
     * route/rate is available for this lane is the Pricing Engine's own job now (any
     * caller of {@link PricingEngine#calculate} gets it, not just this one) — see {@code
     * PricingEngineImpl.priceByDistanceAndWeight}.
     */
    private PricingResult priceIt(UUID bookingBranchId, UUID deliveryBranchId,
                                  String pickupPincode, String deliveryPincode,
                                  UUID serviceTypeId, UUID packageTypeId, UUID paymentModeId,
                                  BigDecimal chargeableWeight, BigDecimal declaredValue,
                                  LocalDate bookingDate, BigDecimal freightFactorOverride) {
        PricingCommand command = new PricingCommand(bookingBranchId, deliveryBranchId,
                pickupPincode, deliveryPincode, serviceTypeId, packageTypeId, paymentModeId,
                chargeableWeight, null, null, null, declaredValue, bookingDate, null, null,
                freightFactorOverride);
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

    private ShipmentCharge persistCharges(Shipment shipment, UUID companyId, PricingResult priced,
                                          BigDecimal otherCharges, com.courier.modules.company.domain.Branch bookingBranch) {
        ShipmentCharge charge = toCharge(priced, otherCharges, bookingBranch);
        charge.setCompanyId(companyId);
        charge.setShipmentId(shipment.getId());
        return chargeRepository.save(charge);
    }

    private void replaceCharges(Shipment shipment, UUID companyId, PricingResult priced, BigDecimal otherCharges,
                                com.courier.modules.company.domain.Branch bookingBranch) {
        ShipmentCharge existing = chargeRepository
                .findByShipmentIdWithinCompany(shipment.getId(), companyId)
                .orElseGet(() -> {
                    ShipmentCharge fresh = ShipmentCharge.builder().build();
                    fresh.setCompanyId(companyId);
                    fresh.setShipmentId(shipment.getId());
                    return fresh;
                });
        copyCharge(priced, otherCharges, bookingBranch, existing);
        chargeRepository.save(existing);
    }

    private ShipmentCharge toCharge(PricingResult priced, BigDecimal otherCharges,
                                    com.courier.modules.company.domain.Branch bookingBranch) {
        ShipmentCharge charge = ShipmentCharge.builder().build();
        copyCharge(priced, otherCharges, bookingBranch, charge);
        return charge;
    }

    /** Scale/rounding for money derived from a percentage — matches the column's own
     *  {@code DECIMAL(19,4)}. */
    private static final int MONEY_SCALE = 4;

    /**
     * {@code otherCharges} is manual, typed at booking time — not part of the Pricing
     * Engine's own rate-driven breakup — so it is added on top of {@code priced.netAmount()}
     * rather than folded into the engine's calculation. GST on it is likewise computed here,
     * not by the Pricing Engine (which never sees {@code otherCharges} at all): at the
     * <b>booking</b> branch's own {@code gstPercentage} (V25, the same source the commission
     * percentages below already use), folded straight into the persisted {@code gstAmount} —
     * one combined GST figure, not a second line, so every report/receipt that already reads
     * {@code gstAmount} picks it up for free.
     *
     * <p>The commission breakdown (V28) is computed here from the <b>booking</b> branch's
     * own percentages (V25) — "use this percentage from login branch config", each branch
     * carries its own rate:
     * <ul>
     *   <li>{@code commissionOnBasicFreight = freight * commissionOnBasicFreight%}</li>
     *   <li>{@code branchCommissionOnOtherAmount = otherCharges * (100 -
     *       commissionOnOtherCharges)%} — the branch's remainder after the company's cut</li>
     *   <li>{@code companyCommissionOnBasicFreight = freight *
     *       companyServiceChargePercentage%}</li>
     *   <li>{@code totalCommission = commissionOnBasicFreight +
     *       branchCommissionOnOtherAmount + companyCommissionOnBasicFreight} — every
     *       commission line on this shipment, summed and stored rather than re-derived on
     *       every report</li>
     * </ul>
     * The remaining freight share is company/head-office revenue, not stored separately.
     */
    private void copyCharge(PricingResult priced, BigDecimal otherCharges,
                            com.courier.modules.company.domain.Branch bookingBranch, ShipmentCharge charge) {
        BigDecimal safeOtherCharges = otherCharges == null ? BigDecimal.ZERO : otherCharges;
        BigDecimal freight = priced.freight();
        BigDecimal gstOnOtherCharges = gstOnOtherCharges(safeOtherCharges, bookingBranch);

        BigDecimal branchShareOfOtherCharges = BigDecimal.valueOf(100)
                .subtract(bookingBranch.getCommissionOnOtherCharges());
        BigDecimal commissionOnBasicFreight = percentOf(freight, bookingBranch.getCommissionOnBasicFreight());
        BigDecimal branchCommissionOnOtherAmount = percentOf(safeOtherCharges, branchShareOfOtherCharges);
        BigDecimal companyCommissionOnBasicFreight =
                percentOf(freight, bookingBranch.getCompanyServiceChargePercentage());
        BigDecimal totalCommission = commissionOnBasicFreight
                .add(branchCommissionOnOtherAmount)
                .add(companyCommissionOnBasicFreight);

        charge.setFreight(freight);
        charge.setFuelCharge(priced.fuelCharge());
        charge.setHandlingCharge(priced.handlingCharge());
        charge.setOdaCharge(priced.odaCharge());
        charge.setInsuranceCharge(priced.insuranceCharge());
        charge.setGstAmount(priced.gstAmount().add(gstOnOtherCharges));
        charge.setDiscountAmount(priced.discountAmount());
        charge.setRoundOff(priced.roundOff());
        charge.setOtherCharges(safeOtherCharges);
        charge.setCommissionOnBasicFreight(commissionOnBasicFreight);
        charge.setBranchCommissionOnOtherAmount(branchCommissionOnOtherAmount);
        charge.setCompanyCommissionOnBasicFreight(companyCommissionOnBasicFreight);
        charge.setTotalCommission(totalCommission);
        charge.setNetAmount(priced.netAmount().add(safeOtherCharges).add(gstOnOtherCharges));
        charge.setMatchedRouteId(priced.matchedRoute() == null ? null : priced.matchedRoute().getId());
        charge.setMatchedRateId(priced.matchedRate() == null ? null : priced.matchedRate().getId());
        charge.setAppliedFreightFactor(priced.appliedFreightFactor());
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal gstOnOtherCharges(BigDecimal otherCharges,
                                                com.courier.modules.company.domain.Branch bookingBranch) {
        return percentOf(otherCharges, bookingBranch.getGstPercentage());
    }

    /** Same total {@link #copyCharge} persists as {@code netAmount} — used ahead of that
     *  call too, for the pre-booking wallet-sufficiency check and audit logging, so both
     *  never drift apart. */
    private static BigDecimal netAmountWithOtherCharges(PricingResult priced, BigDecimal otherCharges,
                                                         com.courier.modules.company.domain.Branch bookingBranch) {
        BigDecimal safeOtherCharges = otherCharges == null ? BigDecimal.ZERO : otherCharges;
        return priced.netAmount().add(safeOtherCharges).add(gstOnOtherCharges(safeOtherCharges, bookingBranch));
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
    private String nextShipmentNumber(UUID bookingBranchId, String branchCode) {
        byte[] branchIdBytes = TimeOrderedUuid.toBytes(bookingBranchId);
        branchShipmentSequenceRepository.advance(branchIdBytes);
        long serial = branchShipmentSequenceRepository.nextValue();
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

    /**
     * {@code "DRS" + 6-digit serial}, e.g. {@code DRS000001} — a company-wide counter, one
     * value per bulk {@link #assignOutForDelivery} call. Same collision-free counter
     * contract as {@link #nextShipmentNumber}/{@link #nextTrackingNumber}.
     */
    private String nextDrsNumber(UUID companyId) {
        byte[] companyIdBytes = TimeOrderedUuid.toBytes(companyId);
        companyDrsSequenceRepository.advance(companyIdBytes);
        long serial = companyDrsSequenceRepository.nextValue();
        return "DRS%06d".formatted(serial);
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
