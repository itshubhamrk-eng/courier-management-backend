package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InsuranceCalculatorTest {

    private final InsuranceCalculator calculator = new InsuranceCalculator();

    @Test
    void enabled_whenDeclaredValuePositive() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, new BigDecimal("5000.00"), null, null);
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command, PricingTestSupport.configuration());

        assertThat(calculator.isEnabled(context)).isTrue();
        assertThat(calculator.calculate(context)).isEqualByComparingTo("8.00");
    }

    @Test
    void disabled_whenNoDeclaredValue() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration());

        assertThat(calculator.isEnabled(context)).isFalse();
    }

    @Test
    void disabled_whenConfigurationTurnsInsuranceOff() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, new BigDecimal("5000.00"), null, null);
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command,
                PricingTestSupport.configuration(true, true, false, true,
                        com.courier.modules.pricing.domain.RoundingRule.NONE));

        assertThat(calculator.isEnabled(context)).isFalse();
    }
}
