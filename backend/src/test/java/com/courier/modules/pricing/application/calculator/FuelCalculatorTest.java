package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FuelCalculatorTest {

    private final FuelCalculator calculator = new FuelCalculator();

    @Test
    void enabled_copiesTheMatchedRatesFuelSurcharge() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration());

        assertThat(calculator.isEnabled(context)).isTrue();
        assertThat(calculator.calculate(context)).isEqualByComparingTo("10.00");
    }

    @Test
    void disabled_whenConfigurationTurnsFuelOff() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration(false, true, true, true,
                        com.courier.modules.pricing.domain.RoundingRule.NONE));

        assertThat(calculator.isEnabled(context)).isFalse();
    }
}
