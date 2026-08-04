package com.courier.modules.shipment.application;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.PackageType;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.Route;
import com.courier.modules.master.domain.ServiceType;
import com.courier.modules.pricing.application.PricingEngine;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.shipment.application.command.CreateShipmentCommand;
import com.courier.modules.shipment.application.command.ShipmentItemCommand;
import com.courier.modules.shipment.application.command.UpdateShipmentCommand;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.shipment.application.event.ShipmentEvent;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Shipment Booking rules, with every collaborator mocked — mirrors
 * {@code rate.application.RateServiceImplTest} / {@code customer.application
 * .CustomerServiceImplTest}'s shape. This module orchestrates other modules rather than
 * re-deciding their own rules, so most fixtures here are stand-ins the Pricing Engine (or
 * Master) is trusted to have already validated in its own test suite. Sender/receiver are
 * plain text (V18) — no Customer module mock is wired in here any more. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BOOKING_BRANCH = UUID.randomUUID();
    private static final UUID DELIVERY_BRANCH = UUID.randomUUID();
    private static final UUID SERVICE_TYPE = UUID.randomUUID();
    private static final UUID PACKAGE_TYPE = UUID.randomUUID();
    private static final UUID PAYMENT_MODE = UUID.randomUUID();
    private static final String PICKUP_PINCODE = "411001";
    private static final String DELIVERY_PINCODE = "400008";

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentItemRepository itemRepository;
    @Mock private ShipmentChargeRepository chargeRepository;
    @Mock private ShipmentStatusHistoryRepository historyRepository;
    @Mock private ShipmentDocumentRepository documentRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private BranchShipmentSequenceRepository branchShipmentSequenceRepository;
    @Mock private CompanyShipmentSequenceRepository companyShipmentSequenceRepository;
    @Mock private com.courier.modules.company.application.UserService userService;
    @Mock private com.courier.modules.company.application.BranchService branchService;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;
    @Mock private RateService rateService;
    @Mock private RouteService routeService;
    @Mock private PricingEngine pricingEngine;
    @Mock private WalletService walletService;
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
        signedIn(Roles.COMPANY_ADMIN);

        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));
        when(branchShipmentSequenceRepository.nextValue()).thenReturn(1L);
        when(companyShipmentSequenceRepository.nextValue()).thenReturn(1L);
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE").build());
        when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(historyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(serviceTypeService.getById(SERVICE_TYPE)).thenReturn(serviceType(2));
        when(packageTypeService.getById(PACKAGE_TYPE)).thenReturn(packageType(null));
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(false));

        PricingResult defaultPricing = pricingResult(new BigDecimal("136.00"));
        when(pricingEngine.calculate(any())).thenReturn(defaultPricing);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("a TO_PAY booking computes weight from the item grid, prices through the "
            + "engine, persists items/charges/history, and never touches the wallet")
    void bookingSucceeds() {
        Shipment created = service.create(command());

        assertThat(created.getShipmentNumber()).isEqualTo("PUNE-000001");
        assertThat(created.getTrackingNumber()).matches("\\d{11}");
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.BOOKED);
        assertThat(created.getActualWeight()).isEqualByComparingTo("5.000");
        assertThat(created.getChargeableWeight()).isEqualByComparingTo("5.000");
        assertThat(created.getExpectedDeliveryDate())
                .isEqualTo(created.getBookingDate().plusDays(2));
        assertThat(created.getSenderName()).isEqualTo("Asha Shah");
        assertThat(created.getReceiverName()).isEqualTo("Rahul Verma");
        assertThat(created.getPickupPincode()).isEqualTo(PICKUP_PINCODE);
        assertThat(created.getDeliveryPincode()).isEqualTo(DELIVERY_PINCODE);

        verify(itemRepository).save(any());
        verify(chargeRepository).save(any());
        verify(historyRepository).save(any());
        verify(walletService, never()).getForBranch(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a PREPAID booking checks the wallet balance before committing and "
            + "publishes the AFTER_COMMIT debit event")
    void prepaidBookingChecksBalanceAndPublishesEvent() {
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(true));
        Wallet wallet = mock(Wallet.class);
        when(wallet.getAvailableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(walletService.getForBranch(BOOKING_BRANCH)).thenReturn(wallet);

        Shipment created = service.create(command());

        verify(walletService).getForBranch(BOOKING_BRANCH);
        verify(eventPublisher).publishEvent(any(ShipmentEvent.PrepaidBookingConfirmed.class));
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.BOOKED);
    }

    @Test
    @DisplayName("an insufficient wallet balance refuses the booking before anything is persisted")
    void insufficientBalanceRefused() {
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(true));
        Wallet wallet = mock(Wallet.class);
        when(wallet.getAvailableBalance()).thenReturn(new BigDecimal("10.00"));
        when(walletService.getForBranch(BOOKING_BRANCH)).thenReturn(wallet);

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient wallet balance");

        verify(shipmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a blank sender name/address/contact is refused")
    void blankSenderRejected() {
        assertThatThrownBy(() -> service.create(command("", "", "")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sender");
    }

    @Test
    @DisplayName("a missing pickup or delivery pincode is refused")
    void blankPincodeRejected() {
        CreateShipmentCommand withoutPincode = new CreateShipmentCommand(
                BOOKING_BRANCH, DELIVERY_BRANCH, "", DELIVERY_PINCODE,
                "Asha Shah", "221B Baker Street, Pune", "9876543210",
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care",
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null);

        assertThatThrownBy(() -> service.create(withoutPincode))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pincode");
    }

    @Test
    @DisplayName("actual weight above the package type's maximum is refused")
    void packageTypeCeilingExceeded() {
        when(packageTypeService.getById(PACKAGE_TYPE)).thenReturn(packageType(new BigDecimal("3.000")));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
    }

    // ------------------------------------------------------------------- cancel

    @Test
    @DisplayName("a BOOKED shipment can be cancelled")
    void cancelSucceedsFromBooked() {
        Shipment existing = existingShipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));

        Shipment cancelled = service.cancel(existing.getId(), "changed my mind");

        assertThat(cancelled.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("a DISPATCHED shipment can no longer be cancelled — it has left the branch")
    void cancelRefusedOnceDispatched() {
        Shipment existing = existingShipment(ShipmentStatus.DISPATCHED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(existing.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("left the branch");

        verify(shipmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------- update

    @Test
    @DisplayName("a shipment can only be edited while still BOOKED")
    void updateRefusedOnceNotBooked() {
        Shipment existing = existingShipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), updateCommand(0L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("BOOKED");
    }

    @Test
    @DisplayName("a stale version on update is refused as a conflict")
    void staleVersionRejected() {
        Shipment existing = existingShipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), updateCommand(99L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("updating a still-BOOKED shipment re-prices it and replaces the charge row")
    void updateReprices() {
        Shipment existing = existingShipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));
        when(chargeRepository.findByShipmentIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.empty());
        PricingResult repricedResult = pricingResult(new BigDecimal("200.00"));
        when(pricingEngine.calculate(any())).thenReturn(repricedResult);

        Shipment updated = service.update(existing.getId(), updateCommand(0L));

        assertThat(updated.getDeliveryBranchId()).isEqualTo(DELIVERY_BRANCH);
        assertThat(updated.getReceiverName()).isEqualTo("Rahul Verma (updated)");
        verify(chargeRepository).save(any());
        verify(itemRepository).deleteAllByShipmentIdAndCompanyId(existing.getId(), COMPANY);
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "admin@legacy.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static CreateShipmentCommand command() {
        return command("Asha Shah", "221B Baker Street, Pune", "9876543210");
    }

    private static CreateShipmentCommand command(String senderName, String senderAddress, String senderContact) {
        return new CreateShipmentCommand(
                BOOKING_BRANCH, DELIVERY_BRANCH, PICKUP_PINCODE, DELIVERY_PINCODE,
                senderName, senderAddress, senderContact,
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care",
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null);
    }

    private static UpdateShipmentCommand updateCommand(Long expectedVersion) {
        return new UpdateShipmentCommand(
                expectedVersion, DELIVERY_BRANCH, PICKUP_PINCODE, DELIVERY_PINCODE,
                "Asha Shah", "221B Baker Street, Pune", "9876543210",
                "Rahul Verma (updated)", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "updated",
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null);
    }

    private static Shipment existingShipment(ShipmentStatus status) {
        Shipment shipment = Shipment.builder()
                .shipmentNumber("SHP2607300000001")
                .trackingNumber("AWB2607300000001")
                .bookingDate(LocalDate.of(2026, 7, 30))
                .bookingBranchId(BOOKING_BRANCH)
                .deliveryBranchId(DELIVERY_BRANCH)
                .pickupPincode(PICKUP_PINCODE)
                .deliveryPincode(DELIVERY_PINCODE)
                .senderName("Asha Shah")
                .senderAddress("221B Baker Street, Pune")
                .senderContact("9876543210")
                .receiverName("Rahul Verma")
                .receiverAddress("12 MG Road, Mumbai")
                .receiverContact("9876500000")
                .serviceTypeId(SERVICE_TYPE)
                .packageTypeId(PACKAGE_TYPE)
                .paymentModeId(PAYMENT_MODE)
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

    private static ServiceType serviceType(int deliveryDays) {
        ServiceType serviceType = new ServiceType();
        serviceType.setCode("STD");
        serviceType.setDeliveryDays(deliveryDays);
        return serviceType;
    }

    private static PackageType packageType(BigDecimal maxWeightKg) {
        PackageType packageType = new PackageType();
        packageType.setCode("BOX");
        packageType.setMaxWeightKg(maxWeightKg);
        return packageType;
    }

    private static PaymentMode paymentMode(boolean collectAtBooking) {
        PaymentMode paymentMode = new PaymentMode();
        paymentMode.setCode(collectAtBooking ? "PAID" : "TO_PAY");
        paymentMode.setCollectAtBooking(collectAtBooking);
        paymentMode.setCollectAtDelivery(!collectAtBooking);
        return paymentMode;
    }

    private static PricingResult pricingResult(BigDecimal netAmount) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(UUID.randomUUID());
        when(route.getCode()).thenReturn("PNQ_BOM");
        Rate rate = mock(Rate.class);
        when(rate.getId()).thenReturn(UUID.randomUUID());
        when(rate.getRateCode()).thenReturn("RATE-PNQ-BOM-STD");

        return new PricingResult(route, rate, new BigDecimal("5.000"), BigDecimal.ZERO,
                new BigDecimal("5.000"), new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.70"),
                BigDecimal.ZERO, new BigDecimal("0.30"), netAmount);
    }
}
