package com.courier.modules.pricing.application.calculator;

import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeRepository;
import com.courier.modules.charge.domain.ChargeSetting;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;
import com.courier.modules.master.domain.Route;
import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** The Hamali-style "sum every active charge for this service type/weight" calculator.
 *  {@code ChargeRepository}/{@code ChargeSettingRepository} are mocked — the actual
 *  weight-slab/FACTOR/PERCENTAGE matching logic lives entirely in this class, not in a
 *  query, so a mocked repository plus real domain objects exercises it fully. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicableChargesCalculatorTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private ChargeRepository chargeRepository;
    @Mock private ChargeSettingRepository chargeSettingRepository;

    private ApplicableChargesCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ApplicableChargesCalculator(chargeRepository, chargeSettingRepository);
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void typeAndOrder() {
        assertThat(calculator.type()).isEqualTo(com.courier.modules.pricing.domain.ChargeType.APPLICABLE_CHARGES);
        assertThat(calculator.order()).isEqualTo(55);
        assertThat(calculator.isEnabled(context(new BigDecimal("10.000")))).isTrue();
    }

    @Test
    @DisplayName("no charge configured for the service type prices zero")
    void noChargesConfigured() {
        stubCharges();
        assertThat(calculator.calculate(context(new BigDecimal("10.000")))).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a KG slab charge (Hamali: 0-20/21-40/41-60/61-9999) prices each band correctly")
    void kgSlabBands() {
        UUID chargeId = UUID.randomUUID();
        stubCharges(charge(chargeId));
        stubSettings(chargeId,
                kgSlab("0", "21", "10"),
                kgSlab("21", "41", "15"),
                kgSlab("41", "61", "20"),
                kgSlab("61", "9999", "25"));

        assertThat(calculator.calculate(context(new BigDecimal("10.000")))).isEqualByComparingTo("10.00");
        assertThat(calculator.calculate(context(new BigDecimal("20.999")))).isEqualByComparingTo("10.00");
        assertThat(calculator.calculate(context(new BigDecimal("21.000")))).isEqualByComparingTo("15.00");
        assertThat(calculator.calculate(context(new BigDecimal("40.000")))).isEqualByComparingTo("15.00");
        assertThat(calculator.calculate(context(new BigDecimal("41.000")))).isEqualByComparingTo("20.00");
        assertThat(calculator.calculate(context(new BigDecimal("60.000")))).isEqualByComparingTo("20.00");
        assertThat(calculator.calculate(context(new BigDecimal("61.000")))).isEqualByComparingTo("25.00");
        assertThat(calculator.calculate(context(new BigDecimal("9998.000")))).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("a weight at/above the top band's own upper bound (half-open [from,to)) prices zero")
    void aboveTopBand() {
        UUID chargeId = UUID.randomUUID();
        stubCharges(charge(chargeId));
        stubSettings(chargeId, kgSlab("0", "21", "10"));

        assertThat(calculator.calculate(context(new BigDecimal("21.000")))).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a FACTOR/AMOUNT charge applies regardless of weight")
    void factorAmountAlwaysApplies() {
        UUID chargeId = UUID.randomUUID();
        stubCharges(charge(chargeId));
        stubSettings(chargeId, factor("30", ChargeValueType.AMOUNT));

        assertThat(calculator.calculate(context(new BigDecimal("1.000")))).isEqualByComparingTo("30.00");
        assertThat(calculator.calculate(context(new BigDecimal("5000.000")))).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("a FACTOR/PERCENTAGE charge is a percentage of freight")
    void factorPercentageOfFreight() {
        UUID chargeId = UUID.randomUUID();
        stubCharges(charge(chargeId));
        stubSettings(chargeId, factor("10", ChargeValueType.PERCENTAGE));

        PricingContext ctx = context(new BigDecimal("10.000"));
        ctx.charge(com.courier.modules.pricing.domain.ChargeType.FREIGHT, new BigDecimal("200.00"));

        assertThat(calculator.calculate(ctx)).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("multiple active charges for the service type all sum together")
    void multipleChargesSum() {
        UUID hamaliId = UUID.randomUUID();
        UUID fuelSurchargeId = UUID.randomUUID();
        stubCharges(charge(hamaliId), charge(fuelSurchargeId));
        stubSettings(hamaliId, kgSlab("0", "21", "10"));
        stubSettings(fuelSurchargeId, factor("5", ChargeValueType.AMOUNT));

        assertThat(calculator.calculate(context(new BigDecimal("10.000")))).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("a KM slab charge matches the booking's matched route distance")
    void kmSlabUsesMatchedRouteDistance() {
        UUID chargeId = UUID.randomUUID();
        stubCharges(charge(chargeId));
        stubSettings(chargeId, kmSlab("0", "50", "40"), kmSlab("50", "9999", "80"));

        Route near = new Route();
        near.setDistanceKm(new BigDecimal("30"));
        Route far = new Route();
        far.setDistanceKm(new BigDecimal("120"));

        PricingContext nearCtx = context(new BigDecimal("10.000"));
        nearCtx.matchedRoute(near);
        assertThat(calculator.calculate(nearCtx)).isEqualByComparingTo("40.00");

        PricingContext farCtx = context(new BigDecimal("10.000"));
        farCtx.matchedRoute(far);
        assertThat(calculator.calculate(farCtx)).isEqualByComparingTo("80.00");
    }

    // ---------------------------------------------------------------- fixtures

    private PricingContext context(BigDecimal chargeableWeight) {
        PricingContext ctx = PricingTestSupport.contextWithCandidates(
                List.of(), chargeableWeight, PricingTestSupport.command(chargeableWeight),
                PricingTestSupport.configuration());
        ctx.charge(com.courier.modules.pricing.domain.ChargeType.FREIGHT, BigDecimal.ZERO);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private void stubCharges(Charge... charges) {
        lenient().when(chargeRepository.findAll(any(Specification.class))).thenReturn(List.of(charges));
    }

    private void stubSettings(UUID chargeId, ChargeSetting... settings) {
        lenient().when(chargeSettingRepository.findByCompanyIdAndChargeIdAndStatus(
                eq(COMPANY), eq(chargeId), eq(ChargeStatus.ACTIVE))).thenReturn(List.of(settings));
    }

    private Charge charge(UUID id) {
        Charge charge = Charge.builder().chargeName("X").serviceTypeId(PricingTestSupport.SERVICE_TYPE)
                .status(ChargeStatus.ACTIVE).build();
        charge.setCompanyId(COMPANY);
        charge.setId(id);
        return charge;
    }

    private ChargeSetting kgSlab(String fromKg, String toKg, String value) {
        return setting(builder -> builder.chargeType(ChargeType.SLAB).chargeSlabType(ChargeSlabType.KG)
                .fromKg(new BigDecimal(fromKg)).toKg(new BigDecimal(toKg))
                .chargeValue(new BigDecimal(value)).chargeValueType(ChargeValueType.AMOUNT));
    }

    private ChargeSetting kmSlab(String fromKm, String toKm, String value) {
        return setting(builder -> builder.chargeType(ChargeType.SLAB).chargeSlabType(ChargeSlabType.KM)
                .fromKm(new BigDecimal(fromKm)).toKm(new BigDecimal(toKm))
                .chargeValue(new BigDecimal(value)).chargeValueType(ChargeValueType.AMOUNT));
    }

    private ChargeSetting factor(String value, ChargeValueType valueType) {
        return setting(builder -> builder.chargeType(ChargeType.FACTOR)
                .chargeValue(new BigDecimal(value)).chargeValueType(valueType));
    }

    private ChargeSetting setting(java.util.function.UnaryOperator<ChargeSetting.ChargeSettingBuilder> customize) {
        ChargeSetting.ChargeSettingBuilder builder = ChargeSetting.builder()
                .commissionType(CommissionType.AMOUNT).commissionValue(BigDecimal.ZERO)
                .status(ChargeStatus.ACTIVE);
        ChargeSetting built = customize.apply(builder).build();
        built.setCompanyId(COMPANY);
        built.setId(UUID.randomUUID());
        return built;
    }
}
