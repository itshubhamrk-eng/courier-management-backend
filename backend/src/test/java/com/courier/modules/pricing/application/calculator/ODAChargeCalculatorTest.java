package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.domain.RoundingRule;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ODAChargeCalculatorTest {

    private final ODAChargeCalculator calculator = new ODAChargeCalculator();

    @Test
    void enabled_copiesTheMatchedRatesOdaCharge() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration());

        assertThat(calculator.calculate(context)).isEqualByComparingTo("15.00");
    }

    @Test
    void disabled_whenConfigurationTurnsOdaOff() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration(true, false, true, true, RoundingRule.NONE));

        assertThat(calculator.isEnabled(context)).isFalse();
    }
}
