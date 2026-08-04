package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.domain.ChargeType;
import com.courier.modules.pricing.domain.RoundingRule;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RoundOffCalculatorTest {

    private final RoundOffCalculator calculator = new RoundOffCalculator();

    @Test
    void nearestOne_roundsUp_positiveRoundOff() {
        PricingContext context = contextWithSubtotal(new BigDecimal("135.70"), RoundingRule.NEAREST_ONE);

        // 135.70 -> 136.00, round off = +0.30
        assertThat(calculator.calculate(context)).isEqualByComparingTo("0.30");
    }

    @Test
    void nearestOne_roundsDown_negativeRoundOff() {
        PricingContext context = contextWithSubtotal(new BigDecimal("135.30"), RoundingRule.NEAREST_ONE);

        // 135.30 -> 135.00, round off = -0.30
        assertThat(calculator.calculate(context)).isEqualByComparingTo("-0.30");
    }

    @Test
    void none_producesNoRoundOff() {
        PricingContext context = contextWithSubtotal(new BigDecimal("135.37"), RoundingRule.NONE);

        assertThat(calculator.calculate(context)).isEqualByComparingTo("0.00");
    }

    private PricingContext contextWithSubtotal(BigDecimal totalBeforeRoundOff, RoundingRule rule) {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), PricingTestSupport.command(new BigDecimal("2.000")),
                PricingTestSupport.configuration(true, true, true, true, rule));
        context.charge(ChargeType.FREIGHT, totalBeforeRoundOff);
        return context;
    }
}
