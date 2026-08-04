package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.domain.ChargeType;
import com.courier.modules.rate.domain.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCalculatorTest {

    private final DiscountCalculator calculator = new DiscountCalculator();

    @Test
    void percentageDiscount_takesPrecedenceOverFlatAmount() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, null, new BigDecimal("10"), new BigDecimal("999.00"));
        PricingContext context = givenTotalBeforeDiscount(command, new BigDecimal("118.00"));

        assertThat(calculator.isEnabled(context)).isTrue();
        // 10% of 118.00 = 11.80
        assertThat(calculator.calculate(context)).isEqualByComparingTo("11.80");
    }

    @Test
    void flatAmountDiscount_whenNoPercentageSupplied() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, null, null, new BigDecimal("25.00"));
        PricingContext context = givenTotalBeforeDiscount(command, new BigDecimal("118.00"));

        assertThat(calculator.calculate(context)).isEqualByComparingTo("25.00");
    }

    @Test
    void clampedAtTheTotal_neverGoesNegative() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, null, null, new BigDecimal("500.00"));
        PricingContext context = givenTotalBeforeDiscount(command, new BigDecimal("118.00"));

        assertThat(calculator.calculate(context)).isEqualByComparingTo("118.00");
    }

    @Test
    void disabled_whenNoDiscountRequested() {
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"));
        PricingContext context = givenTotalBeforeDiscount(command, new BigDecimal("118.00"));

        assertThat(calculator.isEnabled(context)).isFalse();
    }

    @Test
    void disabled_whenConfigurationTurnsDiscountsOff() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingCommand command = PricingTestSupport.command(new BigDecimal("2.000"), null, null,
                null, null, new BigDecimal("10"), null);
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command,
                PricingTestSupport.configuration(true, true, true, false,
                        com.courier.modules.pricing.domain.RoundingRule.NONE));

        assertThat(calculator.isEnabled(context)).isFalse();
    }

    private PricingContext givenTotalBeforeDiscount(PricingCommand command, BigDecimal totalBeforeDiscount) {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = PricingTestSupport.contextWithMatchedRate(rate,
                new BigDecimal("2.000"), command, PricingTestSupport.configuration());
        context.charge(ChargeType.FREIGHT, totalBeforeDiscount);
        return context;
    }
}
