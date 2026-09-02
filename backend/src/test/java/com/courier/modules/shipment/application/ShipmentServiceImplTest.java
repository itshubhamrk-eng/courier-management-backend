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
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.shipment.domain.BranchShipmentSequenceRepository;
import com.courier.modules.shipment.domain.CompanyShipmentSequenceRepository;
import com.courier.modules.shipment.domain.DeliveryAssignmentRepository;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentAssetRepository;
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
    private static final UUID CROSSING_BRANCH = UUID.randomUUID();
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
    @Mock private com.courier.modules.shipment.domain.CompanyDrsSequenceRepository companyDrsSequenceRepository;
    @Mock private com.courier.modules.company.application.UserService userService;
    @Mock private com.courier.modules.company.application.BranchService branchService;
    @Mock private com.courier.modules.customer.application.CustomerService customerService;
    @Mock private com.courier.modules.crossing.application.CrossingService crossingService;
    @Mock private com.courier.modules.support.application.TicketService ticketService;
    @Mock private com.courier.modules.support.application.TicketCategoryService ticketCategoryService;
    @Mock private com.courier.modules.ewaybill.application.EwayBillService ewayBillService;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;
    @Mock private RateService rateService;
    @Mock private RouteService routeService;
    @Mock private PricingEngine pricingEngine;
    @Mock private WalletService walletService;
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
                ewayBillService, auditService, eventPublisher, fileStoragePort, shipmentAssetRepository);

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
        assertThat(created.getCurrentLocationId()).isEqualTo(BOOKING_BRANCH);
        assertThat(created.getNextLocationId()).isEqualTo(DELIVERY_BRANCH);

        verify(itemRepository).save(any());
        verify(chargeRepository).save(any());
        verify(historyRepository).save(any());
        verify(walletService, never()).getForBranch(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(customerService).findOrCreateForBooking("Asha Shah", "9876543210");
        verify(customerService).findOrCreateForBooking("Rahul Verma", "9876500000");
        verify(crossingService, never()).createLegs(any(), any(), any());
    }

    @Test
    @DisplayName("a crossing booking sets nextLocationId to the crossing branch (not the "
            + "delivery branch) and creates a CrossingDetail for it")
    void crossingBookingCreatesCrossingDetail() {
        Shipment created = service.create(
                command("Asha Shah", "221B Baker Street, Pune", "9876543210", true, CROSSING_BRANCH));

        assertThat(created.getCurrentLocationId()).isEqualTo(BOOKING_BRANCH);
        assertThat(created.getNextLocationId()).isEqualTo(CROSSING_BRANCH);

        verify(crossingService).createLegs(created.getId(), List.of(CROSSING_BRANCH), null);
    }

    @Test
    @DisplayName("crossing without a crossing branch is refused before anything is persisted")
    void crossingWithoutBranchRejected() {
        assertThatThrownBy(() -> service.create(
                command("Asha Shah", "221B Baker Street, Pune", "9876543210", true, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("crossing");

        verify(shipmentRepository, never()).save(any());
        verify(crossingService, never()).createLegs(any(), any(), any());
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
    @DisplayName("the commission breakdown is computed from the booking branch's own "
            + "percentages and stored on the charge row — branch commission is taken on "
            + "freight AFTER the company's service charge is deducted, not the original freight")
    void commissionComputedFromBookingBranchPercentages() {
        // freight 100.00, otherCharges 0; PUNE's default percentages (V25): commission on
        // basic freight 10%, company service charge 10%, commission on other charges 20%
        // (so the branch keeps 80% of other charges — none here).
        // companyCommissionOnBasicFreight = 100 * 10% = 10
        // remainingFreight               = 100 - 10 = 90
        // commissionOnBasicFreight       = 90 * 10% = 9
        Shipment created = service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("9.0000");
        assertThat(captor.getValue().getBranchCommissionOnOtherAmount()).isEqualByComparingTo("0.0000");
        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("10.0000");
        // totalCommission is every line summed (9 + 0 + 10) — not just the branch's own
        // two, which is the (smaller) amount that actually gets credited to its wallet.
        assertThat(captor.getValue().getTotalCommission()).isEqualByComparingTo("19.0000");
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.BOOKED);
    }

    @Test
    @DisplayName("branch commission on freight after company service charge deduction — "
            + "worked example: freight 100, service charge 10%, branch commission 20% -> "
            + "company cut 10, remaining 90, branch commission 18")
    void commissionOnRemainingFreightWorkedExample() {
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE")
                .commissionOnBasicFreight(new BigDecimal("20.00"))
                .companyServiceChargePercentage(new BigDecimal("10.00"))
                .build());

        service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("10.0000");
        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("18.0000");
    }

    @Test
    @DisplayName("0% company service charge — branch commission is taken on the full, "
            + "un-deducted freight")
    void commissionWithZeroServiceCharge() {
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE")
                .commissionOnBasicFreight(new BigDecimal("20.00"))
                .companyServiceChargePercentage(BigDecimal.ZERO)
                .build());

        service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("0.0000");
        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("20.0000");
    }

    @Test
    @DisplayName("0% branch commission — company still takes its service charge, branch gets "
            + "nothing off freight")
    void commissionWithZeroBranchCommission() {
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE")
                .commissionOnBasicFreight(BigDecimal.ZERO)
                .companyServiceChargePercentage(new BigDecimal("10.00"))
                .build());

        service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("10.0000");
        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("a different freight amount (500) still deducts the company's share first")
    void commissionWithDifferentFreightAmount() {
        PricingResult priced = pricingResult(new BigDecimal("500.00"), new BigDecimal("500.00"));
        when(pricingEngine.calculate(any())).thenReturn(priced);
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE")
                .commissionOnBasicFreight(new BigDecimal("20.00"))
                .companyServiceChargePercentage(new BigDecimal("10.00"))
                .build());

        service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        // companyCommissionOnBasicFreight = 500 * 10% = 50; remaining = 450; branch = 450 * 20% = 90
        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("50.0000");
        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("90.0000");
    }

    @Test
    @DisplayName("a decimal freight amount rounds the same way through both cuts")
    void commissionWithDecimalFreightAmount() {
        PricingResult priced = pricingResult(new BigDecimal("133.33"), new BigDecimal("133.33"));
        when(pricingEngine.calculate(any())).thenReturn(priced);
        when(branchService.getById(any())).thenReturn(Branch.builder().branchCode("PUNE")
                .commissionOnBasicFreight(new BigDecimal("15.00"))
                .companyServiceChargePercentage(new BigDecimal("7.50"))
                .build());

        service.create(command());

        org.mockito.ArgumentCaptor<com.courier.modules.shipment.domain.ShipmentCharge> captor =
                org.mockito.ArgumentCaptor.forClass(com.courier.modules.shipment.domain.ShipmentCharge.class);
        verify(chargeRepository).save(captor.capture());

        // companyCommissionOnBasicFreight = 133.33 * 7.5% = 9.99975 -> 9.9998 (HALF_UP, 4dp)
        // remaining = 133.33 - 9.9998 = 123.3302; branch = 123.3302 * 15% = 18.49953 -> 18.4995
        assertThat(captor.getValue().getCompanyCommissionOnBasicFreight()).isEqualByComparingTo("9.9998");
        assertThat(captor.getValue().getCommissionOnBasicFreight()).isEqualByComparingTo("18.4995");
    }

    @Test
    @DisplayName("a PREPAID booking's commission is not published at booking time any more — "
            + "only the debit event is")
    void prepaidBookingNoLongerPublishesCommissionAtBooking() {
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(true));
        Wallet wallet = mock(Wallet.class);
        when(wallet.getAvailableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(walletService.getForBranch(BOOKING_BRANCH)).thenReturn(wallet);
        when(branchService.getById(any())).thenReturn(
                Branch.builder().branchCode("PUNE").instantCommission(true).build());

        service.create(command());

        verify(eventPublisher, never()).publishEvent(any(ShipmentEvent.DispatchCommissionEarned.class));
        verify(eventPublisher).publishEvent(any(ShipmentEvent.PrepaidBookingConfirmed.class));
    }

    // ------------------------------------------------------------ transitionToDispatched

    @Test
    @DisplayName("dispatching a manifest credits only the branch's own commission (not the "
            + "company's) for a PREPAID shipment when its booking branch has instantCommission on")
    void dispatchPublishesCommissionWhenInstant() {
        Shipment shipment = existingShipment(ShipmentStatus.MANIFEST_CREATED);
        UUID manifestId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        when(shipmentRepository.findAllByCompanyIdAndIdIn(COMPANY, List.of(shipment.getId())))
                .thenReturn(List.of(shipment));
        when(chargeRepository.findByShipmentIdIn(List.of(shipment.getId())))
                .thenReturn(List.of(charge(shipment.getId(), "10.0000", "5.0000")));
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(true));
        when(branchService.getById(BOOKING_BRANCH)).thenReturn(
                Branch.builder().branchCode("PUNE").instantCommission(true).build());

        service.transitionToDispatched(List.of(shipment.getId()), manifestId, vehicleId, BOOKING_BRANCH);

        org.mockito.ArgumentCaptor<ShipmentEvent.DispatchCommissionEarned> captor =
                org.mockito.ArgumentCaptor.forClass(ShipmentEvent.DispatchCommissionEarned.class);
        verify(eventPublisher).publishEvent(captor.capture());
        // commissionOnBasicFreight (10) + branchCommissionOnOtherAmount (5), never the
        // stored totalCommission, which also folds in the company's own cut.
        assertThat(captor.getValue().branchCommission()).isEqualByComparingTo("15.0000");
        assertThat(captor.getValue().shipmentId()).isEqualTo(shipment.getId());
        assertThat(captor.getValue().bookingBranchId()).isEqualTo(BOOKING_BRANCH);
    }

    @Test
    @DisplayName("dispatching a manifest publishes no commission when the booking branch has "
            + "instantCommission off")
    void dispatchSkipsCommissionWhenNotInstant() {
        Shipment shipment = existingShipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findAllByCompanyIdAndIdIn(COMPANY, List.of(shipment.getId())))
                .thenReturn(List.of(shipment));
        when(chargeRepository.findByShipmentIdIn(List.of(shipment.getId())))
                .thenReturn(List.of(charge(shipment.getId(), "10.0000", "5.0000")));
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(true));
        when(branchService.getById(BOOKING_BRANCH)).thenReturn(
                Branch.builder().branchCode("PUNE").instantCommission(false).build());

        service.transitionToDispatched(List.of(shipment.getId()), UUID.randomUUID(), UUID.randomUUID(),
                BOOKING_BRANCH);

        verify(eventPublisher, never()).publishEvent(any(ShipmentEvent.DispatchCommissionEarned.class));
    }

    @Test
    @DisplayName("dispatching a manifest publishes no commission for a TO_PAY/COD shipment")
    void dispatchSkipsCommissionWhenNotCollectAtBooking() {
        Shipment shipment = existingShipment(ShipmentStatus.MANIFEST_CREATED);
        when(shipmentRepository.findAllByCompanyIdAndIdIn(COMPANY, List.of(shipment.getId())))
                .thenReturn(List.of(shipment));
        when(chargeRepository.findByShipmentIdIn(List.of(shipment.getId())))
                .thenReturn(List.of(charge(shipment.getId(), "10.0000", "5.0000")));
        when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode(false));

        service.transitionToDispatched(List.of(shipment.getId()), UUID.randomUUID(), UUID.randomUUID(),
                BOOKING_BRANCH);

        verify(eventPublisher, never()).publishEvent(any(ShipmentEvent.DispatchCommissionEarned.class));
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
                BOOKING_BRANCH, DELIVERY_BRANCH, null, "", DELIVERY_PINCODE,
                "Asha Shah", "221B Baker Street, Pune", "9876543210",
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care", null, null, null,
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null, null, null, null, null, null);

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

    @Test
    @DisplayName("a manually entered shipment number is used verbatim instead of the "
            + "auto-generated one")
    void manualShipmentNumberHonored() {
        Shipment created = service.create(commandWithManualNumber("MANUAL-001"));

        assertThat(created.getShipmentNumber()).isEqualTo("MANUAL-001");
        verify(branchShipmentSequenceRepository, never()).advance(any());
    }

    @Test
    @DisplayName("a manual shipment number already used by this company is refused")
    void manualShipmentNumberDuplicateRejected() {
        when(shipmentRepository.existsByCompanyIdAndShipmentNumber(COMPANY, "MANUAL-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(commandWithManualNumber("MANUAL-001")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in use");

        verify(shipmentRepository, never()).save(any());
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

    // ------------------------------------------------------------- uploadShipmentImage

    @Test
    @DisplayName("uploadShipmentImage refuses a file extension outside the accepted image set")
    void uploadShipmentImageRefusesUnacceptedExtension() {
        Shipment existing = existingShipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.uploadShipmentImage(existing.getId(),
                new ShipmentService.UploadShipmentImageCommand(new byte[]{1}, "photo.mp4", "video/mp4")))
                .isInstanceOf(BusinessRuleException.class);
        verify(fileStoragePort, never()).upload(any());
    }

    @Test
    @DisplayName("uploadShipmentImage stores the file and records it as a BOOKING asset")
    void uploadShipmentImageHappyPath() {
        Shipment existing = existingShipment(ShipmentStatus.BOOKED);
        when(shipmentRepository.findByIdWithinCompany(existing.getId(), COMPANY))
                .thenReturn(Optional.of(existing));
        when(fileStoragePort.upload(any())).thenReturn(new FileStoragePort.StoredFile(
                "https://bucket.s3.amazonaws.com/shipment-photo/x.jpg", "shipment-photo/x.jpg"));

        String url = service.uploadShipmentImage(existing.getId(),
                new ShipmentService.UploadShipmentImageCommand(new byte[]{1, 2, 3}, "photo.JPG", "image/jpeg"));

        assertThat(url).isEqualTo("https://bucket.s3.amazonaws.com/shipment-photo/x.jpg");
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.courier.modules.shipment.domain.ShipmentAsset.class);
        verify(shipmentAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getAssetType())
                .isEqualTo(com.courier.modules.shipment.domain.ShipmentAssetType.BOOKING);
        assertThat(captor.getValue().getKind()).isEqualTo("PHOTO");
        assertThat(captor.getValue().getAssetUrl()).isEqualTo("https://bucket.s3.amazonaws.com/shipment-photo/x.jpg");
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
                BOOKING_BRANCH, DELIVERY_BRANCH, null, PICKUP_PINCODE, DELIVERY_PINCODE,
                senderName, senderAddress, senderContact,
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care", null, null, null,
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null, null, null, null, null, null);
    }

    private static CreateShipmentCommand commandWithManualNumber(String manualShipmentNumber) {
        return new CreateShipmentCommand(
                BOOKING_BRANCH, DELIVERY_BRANCH, manualShipmentNumber, PICKUP_PINCODE, DELIVERY_PINCODE,
                "Asha Shah", "221B Baker Street, Pune", "9876543210",
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care", null, null, null,
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null, null, null, null, null, null);
    }

    private static UpdateShipmentCommand updateCommand(Long expectedVersion) {
        return new UpdateShipmentCommand(
                expectedVersion, DELIVERY_BRANCH, PICKUP_PINCODE, DELIVERY_PINCODE,
                "Asha Shah", "221B Baker Street, Pune", "9876543210",
                "Rahul Verma (updated)", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "updated", null, null, null,
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null, null, null);
    }

    private static CreateShipmentCommand command(String senderName, String senderAddress, String senderContact,
                                                   boolean crossing, java.util.UUID crossingBranchId) {
        return new CreateShipmentCommand(
                BOOKING_BRANCH, DELIVERY_BRANCH, null, PICKUP_PINCODE, DELIVERY_PINCODE,
                senderName, senderAddress, senderContact,
                "Rahul Verma", "12 MG Road, Mumbai", "9876500000",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                null, LocalDate.of(2026, 7, 30), new BigDecimal("1000"), 1, "handle with care", null, null, null,
                List.of(new ShipmentItemCommand("Box", 1, new BigDecimal("5.000"),
                        null, null, null, null, false, false)),
                null, null, null, null, crossing,
                crossingBranchId == null ? null : List.of(crossingBranchId), null, null, null);
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

    private static com.courier.modules.shipment.domain.ShipmentCharge charge(
            UUID shipmentId, String commissionOnBasicFreight, String branchCommissionOnOtherAmount) {
        return com.courier.modules.shipment.domain.ShipmentCharge.builder()
                .shipmentId(shipmentId)
                .commissionOnBasicFreight(new BigDecimal(commissionOnBasicFreight))
                .branchCommissionOnOtherAmount(new BigDecimal(branchCommissionOnOtherAmount))
                .build();
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
        return pricingResult(new BigDecimal("100.00"), netAmount);
    }

    private static PricingResult pricingResult(BigDecimal freight, BigDecimal netAmount) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(UUID.randomUUID());
        when(route.getCode()).thenReturn("PNQ_BOM");
        Rate rate = mock(Rate.class);
        when(rate.getId()).thenReturn(UUID.randomUUID());
        when(rate.getRateCode()).thenReturn("RATE-PNQ-BOM-STD");

        return new PricingResult(route, rate, new BigDecimal("5.000"), BigDecimal.ZERO,
                new BigDecimal("5.000"), freight, new BigDecimal("10.00"),
                new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.70"),
                BigDecimal.ZERO, new BigDecimal("0.30"), netAmount, null);
    }
}
