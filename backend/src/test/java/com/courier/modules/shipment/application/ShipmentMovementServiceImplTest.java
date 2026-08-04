package com.courier.modules.shipment.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.pricing.application.PricingEngine;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignment;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentStatus;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentDocumentRepository;
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
import static org.mockito.Mockito.when;

/** Shipment Movement's own five steps (Out Scan, the shipment-side half of Dispatch, In
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
    private static final UUID MANIFEST = UUID.randomUUID();

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentItemRepository itemRepository;
    @Mock private ShipmentChargeRepository chargeRepository;
    @Mock private ShipmentStatusHistoryRepository historyRepository;
    @Mock private ShipmentDocumentRepository documentRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private BranchShipmentSequenceRepository branchShipmentSequenceRepository;
    @Mock private CompanyShipmentSequenceRepository companyShipmentSequenceRepository;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;
    @Mock private RateService rateService;
    @Mock private RouteService routeService;
    @Mock private PricingEngine pricingEngine;
    @Mock private WalletService walletService;
    @Mock private UserService userService;
    @Mock private BranchService branchService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShipmentServiceImpl(shipmentRepository, itemRepository, chargeRepository,
                historyRepository, documentRepository, deliveryAssignmentRepository,
                branchShipmentSequenceRepository, companyShipmentSequenceRepository,
                serviceTypeService, packageTypeService, paymentModeService,
                rateService, routeService, pricingEngine, new PricingProperties(), walletService,
                userService, branchService, auditService, eventPublisher);
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

    // -------------------------------------------------------------------- inScan

    @Test
    @DisplayName("inScan refuses a shipment that is not DISPATCHED")
    void inScanRefusesWrongStatus() {
        Shipment shipment = shipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()));

        assertThat(result.failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("inScan refuses when the receiving branch does not match the shipment's delivery branch")
    void inScanRefusesWrongBranch() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(UUID.randomUUID(), List.of(shipment.getTrackingNumber()));

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
    }

    @Test
    @DisplayName("inScan moves a DISPATCHED shipment received at its own delivery branch to IN_SCAN")
    void inScanHappyPath() {
        Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByCompanyIdAndTrackingNumber(COMPANY, shipment.getTrackingNumber()))
                .thenReturn(Optional.of(shipment));

        var result = service.inScan(DELIVERY_BRANCH, List.of(shipment.getTrackingNumber()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_SCAN);
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

        Shipment delivered = service.deliver(shipment.getId(),
                new ShipmentService.DeliverCommand("Rahul Verma", "Left at gate", "1234", null, null));

        assertThat(delivered.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(assignment.getStatus()).isEqualTo(DeliveryAssignmentStatus.DELIVERED);
        assertThat(assignment.getReceiverName()).isEqualTo("Rahul Verma");
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
}
