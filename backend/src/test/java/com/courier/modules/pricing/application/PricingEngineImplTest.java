package com.courier.modules.pricing.application;

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
import static org.mockito.ArgumentMatchers.any;
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

    private PricingEngineImpl engine;

    @BeforeEach
    void setUp() {
        PricingProperties properties = new PricingProperties();
        engine = new PricingEngineImpl(routeValidation, bookingValidation, rateValidation,
                weightValidation, pricingFactory, properties);
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
                new BigDecimal("10"), null, bookingDate, null, null);

        when(routeValidation.validate(command)).thenReturn(route);
        when(bookingValidation.validate(command)).thenReturn(bookingDate);
        when(rateValidation.validate(route.getId(), command, bookingDate))
                .thenReturn(List.of(rate));
        when(pricingFactory.resolve(any())).thenReturn(strategy);
        PricingResult expected = new PricingResult(route, rate, new BigDecimal("1.200"),
                new BigDecimal("1.200"), new BigDecimal("1.200"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
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
}
