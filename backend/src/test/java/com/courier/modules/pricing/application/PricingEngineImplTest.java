package com.courier.modules.pricing.application;

import com.courier.modules.freight.application.FreightCalculationResult;
import com.courier.modules.freight.application.FreightFactorService;
import com.courier.modules.freight.domain.FreightFactor;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.Route;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.application.factory.PricingFactory;
import com.courier.modules.pricing.application.strategy.PricingStrategy;
import com.courier.modules.pricing.application.validation.BookingValidation;
import com.courier.modules.pricing.application.validation.RateValidation;
import com.courier.modules.pricing.application.validation.RouteValidation;
import com.courier.modules.pricing.application.validation.WeightValidation;
import com.courier.modules.pricing.domain.RoundingRule;
import com.courier.modules.rate.domain.Rate;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.RouteRateUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** End-to-end wiring: every step delegates and hands its result to the next, and the
 * weight computed here (volumetric, then chargeable) is what reaches the strategy. */
@ExtendWith(MockitoExtension.class)
class PricingEngineImplTest {

    @Mock private RouteValidation routeValidation;
    @Mock private BookingValidation bookingValidation;
    @Mock private RateValidation rateValidation;
    @Mock private WeightValidation weightValidation;
    @Mock private PricingFactory pricingFactory;
    @Mock private PricingStrategy strategy;
    @Mock private FreightFactorService freightFactorService;
    @Mock private com.courier.modules.company.application.CompanySettingsService companySettingsService;

    private PricingEngineImpl engine;

    @BeforeEach
    void setUp() {
        PricingProperties properties = new PricingProperties();
        engine = new PricingEngineImpl(routeValidation, bookingValidation, rateValidation,
                weightValidation, pricingFactory, properties, freightFactorService, companySettingsService);
    }

    @Test
    void calculate_wiresEveryStageInOrder_andComputesChargeableWeight() {
        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setStatus(MasterStatus.ACTIVE);
        Rate rate = com.courier.modules.pricing.application.PricingTestSupport
                .rate("RATE1", "0.000", "5.000");
        LocalDate bookingDate = LocalDate.of(2026, 6, 1);

        PricingCommand command = new PricingCommand(UUID.randomUUID(), UUID.randomUUID(),
                "411001", "400001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1.000"), new BigDecimal("30"), new BigDecimal("20"),
                new BigDecimal("10"), null, bookingDate, null, null, null);

        when(routeValidation.validate(command)).thenReturn(route);
        when(bookingValidation.validate(command)).thenReturn(bookingDate);
        when(rateValidation.validate(route.getId(), command, bookingDate))
                .thenReturn(List.of(rate));
        when(pricingFactory.resolve(any())).thenReturn(strategy);
        PricingResult expected = new PricingResult(route, rate, new BigDecimal("1.200"),
                new BigDecimal("1.200"), new BigDecimal("1.200"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        when(strategy.price(any())).thenReturn(expected);

        PricingResult result = engine.calculate(command);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<PricingContext> captor = ArgumentCaptor.forClass(PricingContext.class);
        verify(strategy).price(captor.capture());
        PricingContext context = captor.getValue();

        assertThat(context.matchedRoute()).isSameAs(route);
        assertThat(context.candidates()).containsExactly(rate);
        assertThat(context.actualWeight()).isEqualByComparingTo("1.000");
        // 30 x 20 x 10 / 5000 = 1.200 -> heavier than the 1.000 actual weight
        assertThat(context.volumetricWeight()).isEqualByComparingTo("1.200");
        assertThat(context.chargeableWeight()).isEqualByComparingTo("1.200");
        assertThat(context.configuration().roundingRule()).isEqualTo(RoundingRule.NEAREST_ONE);
    }

    @Test
    void calculate_fallsBackToFreightFactor_whenNoRouteRateForTheLane() {
        UUID bookingBranchId = UUID.randomUUID();
        UUID deliveryBranchId = UUID.randomUUID();
        PricingCommand command = new PricingCommand(bookingBranchId, deliveryBranchId,
                "411001", "400001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.000"), null, null, null, null,
                LocalDate.of(2026, 6, 1), null, null, null);

        when(routeValidation.validate(command)).thenThrow(
                new RouteRateUnavailableException("No route runs from branch %s to branch %s."
                        .formatted(bookingBranchId, deliveryBranchId)));

        FreightFactor matched = mock(FreightFactor.class);
        when(matched.getFactor()).thenReturn(new BigDecimal("7.50"));
        when(freightFactorService.calculate(any())).thenReturn(new FreightCalculationResult(
                matched, new BigDecimal("148.728"), new BigDecimal("5.000"), new BigDecimal("37.50")));
        when(companySettingsService.get()).thenReturn(
                com.courier.modules.company.domain.CompanySettings.builder()
                        .gstPercentage(BigDecimal.ZERO).build());

        PricingResult result = engine.calculate(command);

        assertThat(result.matchedRoute()).isNull();
        assertThat(result.matchedRate()).isNull();
        assertThat(result.freight()).isEqualByComparingTo("37.50");
        assertThat(result.fuelCharge()).isEqualByComparingTo("0");
        assertThat(result.netAmount()).isEqualByComparingTo("37.50");
        assertThat(result.appliedFreightFactor()).isEqualByComparingTo("7.50");
        verify(pricingFactory, never()).resolve(any());
    }

    @Test
    void calculate_freightFactorOverride_mustBeAtLeastTheMatchedFactor() {
        UUID bookingBranchId = UUID.randomUUID();
        UUID deliveryBranchId = UUID.randomUUID();
        PricingCommand command = new PricingCommand(bookingBranchId, deliveryBranchId,
                "411001", "400001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.000"), null, null, null, null,
                LocalDate.of(2026, 6, 1), null, null, new BigDecimal("5.00"));

        when(routeValidation.validate(command)).thenThrow(
                new RouteRateUnavailableException("No route runs from branch %s to branch %s."
                        .formatted(bookingBranchId, deliveryBranchId)));

        FreightFactor matched = mock(FreightFactor.class);
        when(matched.getFactor()).thenReturn(new BigDecimal("7.50"));
        when(freightFactorService.calculate(any())).thenReturn(new FreightCalculationResult(
                matched, new BigDecimal("148.728"), new BigDecimal("5.000"), new BigDecimal("37.50")));

        assertThatThrownBy(() -> engine.calculate(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can only be increased");
    }

    @Test
    void calculate_freightFactorOverride_pricesAtTheHigherFactor() {
        UUID bookingBranchId = UUID.randomUUID();
        UUID deliveryBranchId = UUID.randomUUID();
        PricingCommand command = new PricingCommand(bookingBranchId, deliveryBranchId,
                "411001", "400001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.000"), null, null, null, null,
                LocalDate.of(2026, 6, 1), null, null, new BigDecimal("10.00"));

        when(routeValidation.validate(command)).thenThrow(
                new RouteRateUnavailableException("No route runs from branch %s to branch %s."
                        .formatted(bookingBranchId, deliveryBranchId)));

        FreightFactor matched = mock(FreightFactor.class);
        when(matched.getFactor()).thenReturn(new BigDecimal("7.50"));
        when(freightFactorService.calculate(any())).thenReturn(new FreightCalculationResult(
                matched, new BigDecimal("148.728"), new BigDecimal("5.000"), new BigDecimal("37.50")));
        when(companySettingsService.get()).thenReturn(
                com.courier.modules.company.domain.CompanySettings.builder()
                        .gstPercentage(BigDecimal.ZERO).build());

        PricingResult result = engine.calculate(command);

        // 10.00 x 5.000 = 50.00, not the matched cell's 37.50
        assertThat(result.freight()).isEqualByComparingTo("50.00");
        assertThat(result.appliedFreightFactor()).isEqualByComparingTo("10.00");
        assertThat(result.netAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void calculate_doesNotFallBack_onAPricingRefusalUnrelatedToRouteRateAvailability() {
        PricingCommand command = new PricingCommand(UUID.randomUUID(), UUID.randomUUID(),
                "411001", "400001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.000"), null, null, null, null,
                LocalDate.of(2026, 6, 1), null, null, null);

        when(routeValidation.validate(command)).thenThrow(
                new BusinessRuleException("Pickup pincode 411001 is not serviceable."));

        assertThatThrownBy(() -> engine.calculate(command))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(RouteRateUnavailableException.class)
                .hasMessageContaining("not serviceable");

        verify(freightFactorService, never()).calculate(any());
    }
}
