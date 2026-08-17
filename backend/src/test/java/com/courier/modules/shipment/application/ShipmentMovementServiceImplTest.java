package com.courier.modules.shipment.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.pricing.application.PricingEngine;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.shipment.application.event.ShipmentEvent;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignment;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentStatus;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentAssetRepository;
import com.courier.modules.shipment.domain.ShipmentAssetType;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentDocumentRepository;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentItemRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Shipment Movement's own four steps (the shipment-side half of Dispatch, In
 * Scan, Out For Delivery, Deliver) — booking/pricing collaborators are unused here and
 * left un-stubbed (lenient), the same style {@code ShipmentServiceImplTest} uses for its
 * own cancel/update-only tests. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentMovementServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BOOKING_BRANCH = UUID.randomUUID();
    private static final UUID DELIVERY_BRANCH = UUID.randomUUID();
    private static final UUID CROSSING_BRANCH = UUID.randomUUID();
    private static final UUID SECOND_CROSSING_BRANCH = UUID.randomUUID();
    private static final UUID MANIFEST = UUID.randomUUID();

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentItemRepository itemRepository;
    @Mock private ShipmentChargeRepository chargeRepository;
    @Mock private ShipmentStatusHistoryRepository historyRepository;
    @Mock private ShipmentDocumentRepository documentRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private BranchShipmentSequenceRepository branchShipmentSequenceRepository;
    @Mock private CompanyShipmentSequenceRepository companyShipmentSequenceRepository;
    @Mock private com.courier.modules.shipment.domain.CompanyDrsSequenceRepository companyDrsSequenceRepository;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;
    @Mock private RateService rateService;
    @Mock private RouteService routeService;
    @Mock private PricingEngine pricingEngine;
    @Mock private WalletService walletService;
    @Mock private UserService userService;
    @Mock private BranchService branchService;
    @Mock private com.courier.modules.customer.application.CustomerService customerService;
    @Mock private com.courier.modules.crossing.application.CrossingService crossingService;
    @Mock private com.courier.modules.support.application.TicketService ticketService;
    @Mock private com.courier.modules.support.application.TicketCategoryService ticketCategoryService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FileStoragePort fileStoragePort;
    @Mock private ShipmentAssetRepository shipmentAssetRepository;

    private ShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShipmentServiceImpl(shipmentRepository, itemRepository, chargeRepository,
                historyRepository, documentRepository, deliveryAssignmentRepository,
                branchShipmentSequenceRepository, companyShipmentSequenceRepository, companyDrsSequenceRepository,
                serviceTypeService, packageTypeService, paymentModeService,
                rateService, routeService, pricingEngine, new PricingProperties(), walletService,
                userService, branchService, customerService, crossingService, ticketService, ticketCategoryService,
                auditService, eventPublisher, fileStoragePort, shipmentAssetRepository);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "ops@test.com", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------- attachToManifest

    @Test
    @DisplayName("attachToManifest refuses a shipment that is not BOOKED")
    void attachRefusesNotBooked() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.attachToManifest(
                shipment.getId(), MANIFEST, BOOKING_BRANCH, DELIVERY_BRANCH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("BOOKED");
    }

    @Test
    @DisplayName("attachToManifest refuses a shipment travelling a different lane")
    void attachRefusesDifferentLane() {
        Shipment shipment = shipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.attachToManifest(
                shipment.getId(), MANIFEST, UUID.randomUUID(), DELIVERY_BRANCH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different lane");
    }

    @Test
    @DisplayName("attachToManifest moves a BOOKED shipment on the right lane to MANIFEST_CREATED")
    void attachHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        Shipment result = service.attachToManifest(shipment.getId(), MANIFEST, BOOKING_BRANCH, DELIVERY_BRANCH);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.MANIFEST_CREATED);
        assertThat(result.getManifestId()).isEqualTo(MANIFEST);
    }

    @Test
    @DisplayName("attachToManifest accepts a READY_FOR_MANIFEST shipment for its second leg, "
            + "matched on current/next location rather than its fixed booking/delivery branch")
    void attachAcceptsSecondLegAfterCrossingHop() {
        Shipment shipment = shipment(ShipmentStatus.READY_FOR_MANIFEST);
        shipment.setCurrentLocationId(CROSSING_BRANCH);
        shipment.setNextLocationId(DELIVERY_BRANCH);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        Shipment result = service.attachToManifest(shipment.getId(), MANIFEST, CROSSING_BRANCH, DELIVERY_BRANCH);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.MANIFEST_CREATED);
    }

    @Test
    @DisplayName("attachToManifest still refuses a second-leg shipment on the wrong lane")
    void attachRefusesSecondLegWrongLane() {
        Shipment shipment = shipment(ShipmentStatus.READY_FOR_MANIFEST);
        shipment.setCurrentLocationId(CROSSING_BRANCH);
        shipment.setNextLocationId(DELIVERY_BRANCH);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.attachToManifest(
                shipment.getId(), MANIFEST, BOOKING_BRANCH, DELIVERY_BRANCH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different lane");
    }

    @Test
    @DisplayName("detachFromManifest reverts a shipment past its first crossing hop to "
            + "READY_FOR_MANIFEST, not BOOKED")
    void detachRevertsToReadyForManifestPastFirstHop() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        shipment.setCurrentLocationId(CROSSING_BRANCH);
        shipment.setNextLocationId(DELIVERY_BRANCH);
        shipment.setManifestId(MANIFEST);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        Shipment result = service.detachFromManifest(shipment.getId(), MANIFEST);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.READY_FOR_MANIFEST);
        assertThat(result.getManifestId()).isNull();
    }

    @Test
    @DisplayName("detachFromManifest still reverts a first-leg shipment to BOOKED")
    void detachRevertsToBookedOnFirstLeg() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        shipment.setCurrentLocationId(BOOKING_BRANCH);
        shipment.setNextLocationId(DELIVERY_BRANCH);
        shipment.setManifestId(MANIFEST);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        Shipment result = service.detachFromManifest(shipment.getId(), MANIFEST);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.BOOKED);
    }

    // -------------------------------------------------------------------- inScan

    @Test
    @DisplayName("inScan refuses a shipment that is not DISPATCHED")
    void inScanRefusesWrongStatus() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()), null, null);

        assertThat(result.failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("inScan refuses when the receiving branch does not match the shipment's delivery branch")
    void inScanRefusesWrongBranch() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(UUID.randomUUID(), List.of(shipment.getTrackingNumber()), null, null);

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
    }

    @Test
    @DisplayName("inScan moves a DISPATCHED shipment received at its own delivery branch to IN_SCAN")
    void inScanHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()), null, null);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_SCAN);
    }

    @Test
    @DisplayName("inScan at a crossing hub (not the final delivery branch) moves the shipment "
            + "to READY_FOR_MANIFEST, not IN_SCAN, and advances nextLocationId")
    void inScanAtCrossingHubAdvancesRoute() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        shipment.setCurrentLocationId(BOOKING_BRANCH);
        shipment.setNextLocationId(CROSSING_BRANCH);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));
        when(crossingService.arriveAt(shipment.getId(), CROSSING_BRANCH))
                .thenReturn(Optional.of(SECOND_CROSSING_BRANCH));

        var result = service.inScan(CROSSING_BRANCH, List.of(shipment.getTrackingNumber()), null, null);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY_FOR_MANIFEST);
        assertThat(shipment.getCurrentLocationId()).isEqualTo(CROSSING_BRANCH);
        assertThat(shipment.getNextLocationId()).isEqualTo(SECOND_CROSSING_BRANCH);
    }

    @Test
    @DisplayName("inScan auto-raises a shortage ticket when the operator names shipments that were "
            + "not physically received, and reports the ticket number back")
    void inScanRaisesShortageTicketForMissing() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));
        when(ticketCategoryService.listCategories()).thenReturn(List.of(
                com.courier.modules.support.domain.TicketCategory.builder().name("Shipment Issue").active(true).build()));
        com.courier.modules.support.domain.Ticket ticket =
                com.courier.modules.support.domain.Ticket.builder().ticketNumber("TKT-000042").build();
        when(ticketService.create(any())).thenReturn(ticket);

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()), "MFT-000001",
                List.of("AWB-MISSING-1", "AWB-MISSING-2"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.shortageTicketNumber()).isEqualTo("TKT-000042");
        verify(ticketService).create(any());
    }

    @Test
    @DisplayName("inScan never touches the ticket service when nothing is reported missing")
    void inScanWithoutShortageRaisesNoTicket() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()), null, List.of());

        assertThat(result.shortageTicketNumber()).isNull();
        verify(ticketService, never()).create(any());
        assertThat(shipment.getManifestId()).isNull();
    }

    @Test
    @DisplayName("inScan at the last crossing hub routes nextLocationId straight to the "
            + "delivery branch once CrossingService reports no further hop")
    void inScanAtLastCrossingHopRoutesToDeliveryBranch() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        shipment.setCurrentLocationId(BOOKING_BRANCH);
        shipment.setNextLocationId(CROSSING_BRANCH);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));
        when(crossingService.arriveAt(shipment.getId(), CROSSING_BRANCH)).thenReturn(Optional.empty());

        service.inScan(CROSSING_BRANCH, List.of(shipment.getTrackingNumber()), null, null);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY_FOR_MANIFEST);
        assertThat(shipment.getNextLocationId()).isEqualTo(DELIVERY_BRANCH);
    }

    // -------------------------------------------------------------------- assignOutForDelivery

    @Test
    @DisplayName("assignOutForDelivery refuses a shipment that is not IN_SCAN")
    void outForDeliveryRefusesWrongStatus() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        var result = service.assignOutForDelivery(List.of(shipment.getId()), UUID.randomUUID());

        assertThat(result.failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("assignOutForDelivery creates a DeliveryAssignment and moves the shipment to OUT_FOR_DELIVERY")
    void outForDeliveryHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.IN_SCAN);
        UUID deliveryUser = UUID.randomUUID();
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.empty());
        when(deliveryAssignmentRepository.save(any(DeliveryAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.assignOutForDelivery(List.of(shipment.getId()), deliveryUser);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
    }

    // -------------------------------------------------------------------- deliver

    @Test
    @DisplayName("deliver refuses a shipment that is not OUT_FOR_DELIVERY")
    void deliverRefusesWrongStatus() {
        Shipment shipment = shipment(ShipmentStatus.IN_SCAN);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand("Rahul", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("OUT_FOR_DELIVERY");
    }

    @Test
    @DisplayName("deliver requires a receiver name")
    void deliverRequiresReceiverName() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .shipmentId(shipment.getId()).status(DeliveryAssignmentStatus.ASSIGNED).build();
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand(" ", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("receiver name");
    }

    @Test
    @DisplayName("deliver closes the assignment and moves the shipment to DELIVERED")
    void deliverHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .shipmentId(shipment.getId()).status(DeliveryAssignmentStatus.ASSIGNED).build();
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(assignment));
        when(deliveryAssignmentRepository.save(any(DeliveryAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentModeService.getById(shipment.getPaymentModeId())).thenReturn(paymentMode(true));
        when(branchService.getById(shipment.getDeliveryBranchId()))
                .thenReturn(Branch.builder().branchCode("PUNE").build());

        Shipment delivered = service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand("Rahul Verma", "Left at gate", "1234", null, null));

        assertThat(delivered.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.DELIVERED);
        assertThat(assignment.getReceiverName()).isEqualTo("Rahul Verma");
        verify(eventPublisher, never()).publishEvent(any(ShipmentEvent.CodCollectedAtDelivery.class));
    }

    @Test
    @DisplayName("deliver publishes a DRS charge debit event for the delivery branch, "
            + "drsChargePerQty * total item quantity")
    void deliverPublishesDrsChargeEvent() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .shipmentId(shipment.getId()).status(DeliveryAssignmentStatus.ASSIGNED).build();
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(assignment));
        when(deliveryAssignmentRepository.save(any(DeliveryAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentModeService.getById(shipment.getPaymentModeId())).thenReturn(paymentMode(true));
        when(branchService.getById(shipment.getDeliveryBranchId()))
                .thenReturn(Branch.builder().branchCode("PUNE")
                        .drsChargePerQty(new BigDecimal("5.00")).build());
        when(itemRepository.findAllByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(List.of(
                        ShipmentItem.builder().quantity(2).build(),
                        ShipmentItem.builder().quantity(1).build()));

        service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand("Rahul Verma", "Left at gate", "1234", null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.DrsChargeApplicable.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().deliveryBranchId()).isEqualTo(DELIVERY_BRANCH);
        assertThat(captor.getValue().shipmentNumber()).isEqualTo(shipment.getShipmentNumber());
        assertThat(captor.getValue().drsCharge()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("deliver publishes a wallet-debit event for the delivery branch when the payment mode collects at delivery")
    void deliverCollectAtDeliveryPublishesCodEvent() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .shipmentId(shipment.getId()).status(DeliveryAssignmentStatus.ASSIGNED).build();
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(assignment));
        when(deliveryAssignmentRepository.save(any(DeliveryAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentModeService.getById(shipment.getPaymentModeId())).thenReturn(paymentMode(false));
        ShipmentCharge charge = ShipmentCharge.builder().netAmount(new BigDecimal("450.0000")).build();
        when(chargeRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(charge));
        when(branchService.getById(shipment.getDeliveryBranchId()))
                .thenReturn(Branch.builder().branchCode("PUNE").build());

        service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand("Rahul Verma", "Left at gate", "1234", null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(ShipmentEvent.CodCollectedAtDelivery.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().deliveryBranchId()).isEqualTo(DELIVERY_BRANCH);
        assertThat(captor.getValue().shipmentNumber()).isEqualTo(shipment.getShipmentNumber());
        assertThat(captor.getValue().netAmount()).isEqualByComparingTo("450.0000");
    }

    @Test
    @DisplayName("deliver records signature/photo urls as POD ShipmentAsset rows, not on the assignment")
    void deliverRecordsPodAssets() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .shipmentId(shipment.getId()).status(DeliveryAssignmentStatus.ASSIGNED).build();
        when(deliveryAssignmentRepository.findByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(assignment));
        when(deliveryAssignmentRepository.save(any(DeliveryAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentModeService.getById(shipment.getPaymentModeId())).thenReturn(paymentMode(true));
        when(branchService.getById(shipment.getDeliveryBranchId()))
                .thenReturn(Branch.builder().branchCode("PUNE").build());

        service.deliver(shipment.getId(), new ShipmentService.DeliverCommand(
                "Rahul Verma", "Left at gate", "1234", "https://x/sig.png", "https://x/photo.png"));

        var captor = org.mockito.ArgumentCaptor.forClass(ShipmentAsset.class);
        verify(shipmentAssetRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ShipmentAsset::getKind)
                .containsExactlyInAnyOrder("SIGNATURE", "PHOTO");
        assertThat(captor.getAllValues()).allMatch(a -> a.getAssetType() == ShipmentAssetType.POD);
    }

    // -------------------------------------------------------------------- POD upload

    @Test
    @DisplayName("uploadPodFile refuses a kind other than PHOTO/SIGNATURE")
    void uploadPodFileRefusesUnknownKind() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.uploadPodFile(shipment.getId(),
                new ShipmentService.UploadPodFileCommand(new byte[]{1}, "proof.jpg", "image/jpeg", "VIDEO")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kind");
        verify(fileStoragePort, never()).upload(any());
    }

    @Test
    @DisplayName("uploadPodFile refuses a file extension outside the accepted set")
    void uploadPodFileRefusesUnacceptedExtension() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.uploadPodFile(shipment.getId(),
                new ShipmentService.UploadPodFileCommand(new byte[]{1}, "proof.exe", "application/octet-stream", "PHOTO")))
                .isInstanceOf(BusinessRuleException.class);
        verify(fileStoragePort, never()).upload(any());
    }

    @Test
    @DisplayName("uploadPodFile stores the file and returns the URL the port hands back")
    void uploadPodFileHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        when(fileStoragePort.upload(any())).thenReturn(
                new FileStoragePort.StoredFile("https://bucket.s3.amazonaws.com/pod/x.jpg", "pod/x.jpg"));

        String url = service.uploadPodFile(shipment.getId(),
                new ShipmentService.UploadPodFileCommand(new byte[]{1, 2, 3}, "photo.JPG", "image/jpeg", "photo"));

        assertThat(url).isEqualTo("https://bucket.s3.amazonaws.com/pod/x.jpg");
        var captor = org.mockito.ArgumentCaptor.forClass(FileStoragePort.UploadRequest.class);
        verify(fileStoragePort).upload(captor.capture());
        assertThat(captor.getValue().keyPrefix()).isEqualTo("pod");
        assertThat(captor.getValue().filename()).contains(shipment.getId().toString()).endsWith(".jpg");
    }

    @Test
    @DisplayName("uploadPodFile surfaces the port's refusal when no storage backend is configured")
    void uploadPodFileBubblesUnconfiguredStorage() {
        Shipment shipment = shipment(ShipmentStatus.OUT_FOR_DELIVERY);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        when(fileStoragePort.upload(any()))
                .thenThrow(new BusinessRuleException("File upload is not available: no storage backend is configured."));

        assertThatThrownBy(() -> service.uploadPodFile(shipment.getId(),
                new ShipmentService.UploadPodFileCommand(new byte[]{1}, "photo.jpg", "image/jpeg", "PHOTO")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no storage backend");
    }

    // -------------------------------------------------------------------- timeline

    @Test
    @DisplayName("timeline reports every named step, completed only for statuses actually reached")
    void timelineMarksCompletedSteps() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findByIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(Optional.of(shipment));
        when(historyRepository.findAllByShipmentIdWithinCompany(shipment.getId(), COMPANY))
                .thenReturn(List.of());

        var steps = service.timeline(shipment.getId());

        assertThat(steps).hasSize(6);
        assertThat(steps).allMatch(s -> !s.completed());
    }

    // ---------------------------------------------------------------------- helpers

    private static Shipment shipment(ShipmentStatus status) {
        Shipment shipment = Shipment.builder()
                .shipmentNumber("SHP2607300000001")
                .trackingNumber("AWB" + UUID.randomUUID().toString().substring(0, 12))
                .bookingDate(LocalDate.of(2026, 7, 30))
                .bookingBranchId(BOOKING_BRANCH)
                .deliveryBranchId(DELIVERY_BRANCH)
                .pickupPincode("411001")
                .deliveryPincode("400008")
                .senderName("Asha Shah")
                .senderAddress("221B Baker Street, Pune")
                .senderContact("9876543210")
                .receiverName("Rahul Verma")
                .receiverAddress("12 MG Road, Mumbai")
                .receiverContact("9876500000")
                .serviceTypeId(UUID.randomUUID())
                .packageTypeId(UUID.randomUUID())
                .paymentModeId(UUID.randomUUID())
                .actualWeight(new BigDecimal("5.000"))
                .volumetricWeight(BigDecimal.ZERO)
                .chargeableWeight(new BigDecimal("5.000"))
                .numberOfPackages(1)
                .status(status)
                .build();
        shipment.setId(UUID.randomUUID());
        shipment.setCompanyId(COMPANY);
        shipment.setVersion(0L);
        return shipment;
    }

    private static PaymentMode paymentMode(boolean collectAtBooking) {
        PaymentMode paymentMode = new PaymentMode();
        paymentMode.setCode(collectAtBooking ? "PAID" : "COD");
        paymentMode.setCollectAtBooking(collectAtBooking);
        paymentMode.setCollectAtDelivery(!collectAtBooking);
        return paymentMode;
    }
}
