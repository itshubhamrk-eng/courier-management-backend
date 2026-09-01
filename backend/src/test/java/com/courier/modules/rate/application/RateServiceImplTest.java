package com.courier.modules.rate.application;

import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.PackageType;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.Route;
import com.courier.modules.master.domain.ServiceType;
import com.courier.modules.rate.application.command.CreateRateCommand;
import com.courier.modules.rate.application.command.RateCalculationCommand;
import com.courier.modules.rate.application.command.UpdateRateCommand;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.rate.domain.RateRepository;
import com.courier.modules.rate.domain.RateStatus;
import com.courier.modules.rate.domain.WeightUnit;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rate rules, with the repository, audit trail and Master's services all mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID ROUTE = UUID.randomUUID();
    private static final UUID SERVICE_TYPE = UUID.randomUUID();
    private static final UUID PACKAGE_TYPE = UUID.randomUUID();
    private static final UUID PAYMENT_MODE = UUID.randomUUID();
    private static final UUID BOOKING_BRANCH = UUID.randomUUID();
    private static final UUID DELIVERY_BRANCH = UUID.randomUUID();

    @Mock private RateRepository repository;
    @Mock private RouteService routeService;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private PackageTypeService packageTypeService;
    @Mock private PaymentModeService paymentModeService;
    @Mock private AuditService auditService;

    private RateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RateServiceImpl(repository, routeService, serviceTypeService,
                packageTypeService, paymentModeService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(repository.save(any(Rate.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.isCodeTaken(any(), anyString(), any())).thenReturn(false);
        lenient().when(routeService.getById(ROUTE)).thenReturn(activeRoute());
        lenient().when(routeService.findByBranches(BOOKING_BRANCH, DELIVERY_BRANCH)).thenReturn(activeRoute());
        lenient().when(serviceTypeService.getById(SERVICE_TYPE)).thenReturn(serviceType());
        lenient().when(packageTypeService.getById(PACKAGE_TYPE)).thenReturn(packageType());
        lenient().when(paymentModeService.getById(PAYMENT_MODE)).thenReturn(paymentMode());
        lenient().when(repository
                        .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                                any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("a valid rate is created against an active route with no overlap")
    void createSucceeds() {
        Rate created = service.create(createCommand("RATE1", "0.000", "5.000"));

        assertThat(created.getRateCode()).isEqualTo("RATE1");
        assertThat(created.isActive()).isTrue();
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a rate against an inactive route is refused")
    void inactiveRouteRejected() {
        when(routeService.getById(ROUTE)).thenReturn(inactiveRoute());

        assertThatThrownBy(() -> service.create(createCommand("RATE1", "0.000", "5.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a taken rate code is refused")
    void duplicateCodeRejected() {
        when(repository.isCodeTaken(COMPANY, "DUP", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand("DUP", "0.000", "5.000")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown service type is refused")
    void unknownServiceTypeRejected() {
        when(serviceTypeService.getById(SERVICE_TYPE))
                .thenThrow(new ResourceNotFoundException("Service type", SERVICE_TYPE));

        assertThatThrownBy(() -> service.create(createCommand("RATE1", "0.000", "5.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No such service type");
    }

    @Test
    @DisplayName("an overlapping active weight slab for the same combination is refused")
    void overlapRejected() {
        Rate existing = existingRate("OTHER", "2.000", "8.000");
        when(repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        eq(COMPANY), eq(ROUTE), eq(SERVICE_TYPE), eq(PACKAGE_TYPE), eq(PAYMENT_MODE),
                        eq(RateStatus.ACTIVE)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(createCommand("RATE1", "0.000", "5.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("slabs sharing a boundary value are refused — both would match that weight")
    void touchingSlabsRejected() {
        Rate existing = existingRate("OTHER", "5.000", "8.000");
        when(repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        eq(COMPANY), eq(ROUTE), eq(SERVICE_TYPE), eq(PACKAGE_TYPE), eq(PAYMENT_MODE),
                        eq(RateStatus.ACTIVE)))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(createCommand("RATE1", "0.000", "5.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("slabs offset by the smallest increment for the same combination are accepted")
    void adjacentSlabsAccepted() {
        Rate existing = existingRate("OTHER", "5.001", "8.000");
        when(repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        eq(COMPANY), eq(ROUTE), eq(SERVICE_TYPE), eq(PACKAGE_TYPE), eq(PAYMENT_MODE),
                        eq(RateStatus.ACTIVE)))
                .thenReturn(List.of(existing));

        Rate created = service.create(createCommand("RATE1", "0.000", "5.000"));

        assertThat(created.getRateCode()).isEqualTo("RATE1");
    }

    // ------------------------------------------------------------------- update

    @Test
    @DisplayName("a stale version on update is refused")
    void staleVersionRejected() {
        Rate existing = existingRate("RATE1", "0.000", "5.000");
        existing.setVersion(3L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), updateCommand("2.000", "6.000", 1L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("activating an already-active rate is idempotent")
    void activateIdempotent() {
        Rate existing = existingRate("RATE1", "0.000", "5.000");
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        Rate activated = service.activate(existing.getId());

        assertThat(activated.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate then reactivate flips status")
    void deactivateThenActivate() {
        Rate existing = existingRate("RATE1", "0.000", "5.000");
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        Rate deactivated = service.deactivate(existing.getId());
        assertThat(deactivated.getStatus()).isEqualTo(RateStatus.INACTIVE);

        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(deactivated));
        Rate reactivated = service.activate(existing.getId());
        assertThat(reactivated.getStatus()).isEqualTo(RateStatus.ACTIVE);
    }

    @Test
    @DisplayName("reactivating a rate whose route went inactive meanwhile is refused")
    void reactivateWithInactiveRouteRejected() {
        Rate existing = existingRate("RATE1", "0.000", "5.000");
        existing.deactivate();
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));
        when(routeService.getById(ROUTE)).thenReturn(inactiveRoute());

        assertThatThrownBy(() -> service.activate(existing.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    // ------------------------------------------------------------------ calculate

    @Test
    @DisplayName("an exact slab match prices at the base rate, plus GST")
    void calculateExactSlabMatch() {
        Rate rate = existingRate("RATE1", "0.000", "5.000");
        rate.setBaseRate(new BigDecimal("100.00"));
        rate.setFuelSurcharge(new BigDecimal("10.00"));
        rate.setGstPercentage(new BigDecimal("18"));
        stubCandidates(rate);

        RateCalculationResult result = service.calculate(calcCommand("2.000"));

        assertThat(result.matchedRate()).isSameAs(rate);
        assertThat(result.chargeableWeight()).isEqualByComparingTo("2.000");
        assertThat(result.freight()).isEqualByComparingTo("100.00");
        // (100 + 10) * 18% = 19.80
        assertThat(result.gstAmount()).isEqualByComparingTo("19.80");
        assertThat(result.totalAmount()).isEqualByComparingTo("129.80");
    }

    @Test
    @DisplayName("weight beyond the top slab is billed as base rate plus overage increments")
    void calculateOverage() {
        Rate rate = existingRate("RATE1", "0.000", "5.000");
        rate.setBaseRate(new BigDecimal("100.00"));
        rate.setAdditionalWeight(new BigDecimal("0.500"));
        rate.setAdditionalWeightRate(new BigDecimal("20.00"));
        rate.setGstPercentage(BigDecimal.ZERO);
        stubCandidates(rate);

        // 1.2 kg over the 5 kg cap -> ceil(1.2 / 0.5) = 3 increments -> 100 + 3*20 = 160
        RateCalculationResult result = service.calculate(calcCommand("6.200"));

        assertThat(result.freight()).isEqualByComparingTo("160.00");
        assertThat(result.chargeableWeight()).isEqualByComparingTo("6.500");
    }

    @Test
    @DisplayName("weight below the lowest slab is floored at that slab's minimum")
    void calculateBelowLowestSlab() {
        Rate rate = existingRate("RATE1", "1.000", "5.000");
        rate.setBaseRate(new BigDecimal("100.00"));
        rate.setGstPercentage(BigDecimal.ZERO);
        stubCandidates(rate);

        RateCalculationResult result = service.calculate(calcCommand("0.500"));

        assertThat(result.chargeableWeight()).isEqualByComparingTo("1.000");
        assertThat(result.freight()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("the minimum charge floors freight below it")
    void calculateMinimumChargeFloors() {
        Rate rate = existingRate("RATE1", "0.000", "5.000");
        rate.setBaseRate(new BigDecimal("50.00"));
        rate.setMinimumCharge(new BigDecimal("75.00"));
        rate.setGstPercentage(BigDecimal.ZERO);
        stubCandidates(rate);

        RateCalculationResult result = service.calculate(calcCommand("2.000"));

        assertThat(result.freight()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("a gap between configured slabs matches nothing")
    void calculateGapBetweenSlabsRejected() {
        Rate low = existingRate("LOW", "0.000", "2.000");
        Rate high = existingRate("HIGH", "5.000", "10.000");
        stubCandidates(low, high);

        assertThatThrownBy(() -> service.calculate(calcCommand("3.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("gap")
                .hasMessageContaining("3.000")
                .matches(e -> !e.getMessage().contains("%s"));
    }

    @Test
    @DisplayName("no route between the two branches is refused")
    void calculateNoRouteRejected() {
        when(routeService.findByBranches(BOOKING_BRANCH, DELIVERY_BRANCH))
                .thenThrow(new BusinessRuleException("No route runs from branch A to branch B."));

        assertThatThrownBy(() -> service.calculate(calcCommand("2.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No route");
    }

    @Test
    @DisplayName("an inactive route cannot be priced")
    void calculateInactiveRouteRejected() {
        when(routeService.findByBranches(BOOKING_BRANCH, DELIVERY_BRANCH)).thenReturn(inactiveRoute());

        assertThatThrownBy(() -> service.calculate(calcCommand("2.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("a booking date outside every candidate rate's effective window matches nothing")
    void calculateOutsideEffectiveWindowRejected() {
        Rate rate = existingRate("RATE1", "0.000", "5.000");
        rate.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        rate.setEffectiveTo(LocalDate.of(2020, 12, 31));
        stubCandidates(rate);

        assertThatThrownBy(() -> service.calculate(calcCommand("2.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active rate is effective on 2026-06-01")
                .matches(e -> !e.getMessage().contains("%s"));
    }

    // -------------------------------------------------------------------- helpers

    private void stubCandidates(Rate... rates) {
        when(repository
                .findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
                        eq(COMPANY), eq(ROUTE), eq(SERVICE_TYPE), eq(PACKAGE_TYPE), eq(PAYMENT_MODE),
                        eq(RateStatus.ACTIVE)))
                .thenReturn(List.of(rates));
    }

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "admin@legacy.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Route activeRoute() {
        Route route = new Route();
        route.setCode("PUNE_MUM");
        route.setName("Pune -> Mumbai");
        route.setStatus(MasterStatus.ACTIVE);
        route.setBookingBranchId(BOOKING_BRANCH);
        route.setDeliveryBranchId(DELIVERY_BRANCH);
        route.setCompanyId(COMPANY);
        return withId(route, ROUTE);
    }

    private static Route inactiveRoute() {
        Route route = activeRoute();
        route.setStatus(MasterStatus.INACTIVE);
        return route;
    }

    private static ServiceType serviceType() {
        ServiceType type = new ServiceType();
        type.setCode("STD");
        type.setName("Standard");
        type.setStatus(MasterStatus.ACTIVE);
        return withId(type, SERVICE_TYPE);
    }

    private static PackageType packageType() {
        PackageType type = new PackageType();
        type.setCode("PARCEL");
        type.setName("Parcel");
        type.setStatus(MasterStatus.ACTIVE);
        return withId(type, PACKAGE_TYPE);
    }

    private static PaymentMode paymentMode() {
        PaymentMode mode = new PaymentMode();
        mode.setCode("PAID");
        mode.setName("Paid");
        mode.setStatus(MasterStatus.ACTIVE);
        return withId(mode, PAYMENT_MODE);
    }

    private static <T extends com.courier.shared.domain.BaseEntity> T withId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }

    private static CreateRateCommand createCommand(String code, String min, String max) {
        return new CreateRateCommand(code, "Rate " + code, ROUTE, SERVICE_TYPE, PACKAGE_TYPE,
                PAYMENT_MODE, new BigDecimal(min), new BigDecimal(max), WeightUnit.KG,
                new BigDecimal("100.00"), new BigDecimal("0.500"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("18"), LocalDate.of(2026, 1, 1), null);
    }

    private static UpdateRateCommand updateCommand(String min, String max, Long expectedVersion) {
        return new UpdateRateCommand("Updated", ROUTE, SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE,
                new BigDecimal(min), new BigDecimal(max), WeightUnit.KG,
                new BigDecimal("100.00"), new BigDecimal("0.500"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("18"), LocalDate.of(2026, 1, 1), null, expectedVersion);
    }

    private static RateCalculationCommand calcCommand(String actualWeight) {
        return new RateCalculationCommand(BOOKING_BRANCH, DELIVERY_BRANCH, SERVICE_TYPE,
                PACKAGE_TYPE, PAYMENT_MODE, new BigDecimal(actualWeight), LocalDate.of(2026, 6, 1));
    }

    private static Rate existingRate(String code, String min, String max) {
        Rate rate = Rate.builder()
                .rateCode(code)
                .rateName("Rate " + code)
                .routeId(ROUTE)
                .serviceTypeId(SERVICE_TYPE)
                .packageTypeId(PACKAGE_TYPE)
                .paymentModeId(PAYMENT_MODE)
                .minimumWeight(new BigDecimal(min))
                .maximumWeight(new BigDecimal(max))
                .weightUnit(WeightUnit.KG)
                .baseRate(new BigDecimal("100.00"))
                .additionalWeight(new BigDecimal("0.500"))
                .additionalWeightRate(new BigDecimal("20.00"))
                .minimumCharge(BigDecimal.ZERO)
                .fuelSurcharge(BigDecimal.ZERO)
                .handlingCharge(BigDecimal.ZERO)
                .odaCharge(BigDecimal.ZERO)
                .insuranceCharge(BigDecimal.ZERO)
                .gstPercentage(new BigDecimal("18"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(RateStatus.ACTIVE)
                .build();
        rate.setCompanyId(COMPANY);
        rate.setVersion(0L);
        return rate;
    }
}
