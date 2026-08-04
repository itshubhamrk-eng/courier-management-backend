package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingTestSupport;
import com.courier.modules.rate.domain.Rate;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreightCalculatorTest {

    private final FreightCalculator calculator = new FreightCalculator();

    @Test
    @DisplayName("an exact slab match prices at the base rate and records the matched rate")
    void exactSlabMatch() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = contextFor(List.of(rate), "2.000");

        BigDecimal freight = calculator.calculate(context);

        assertThat(freight).isEqualByComparingTo("100.00");
        assertThat(context.matchedRate()).isSameAs(rate);
    }

    @Test
    @DisplayName("weight below the lowest slab is floored at that slab's minimum")
    void belowLowestSlab() {
        Rate rate = PricingTestSupport.rate("RATE1", "1.000", "5.000");
        PricingContext context = contextFor(List.of(rate), "0.500");

        assertThat(calculator.calculate(context)).isEqualByComparingTo("100.00");
        assertThat(context.matchedRate()).isSameAs(rate);
    }

    @Test
    @DisplayName("weight above the top slab bills base rate plus overage increments")
    void overageAboveTopSlab() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        PricingContext context = contextFor(List.of(rate), "6.200");

        // 1.2 kg over the 5 kg cap -> ceil(1.2 / 0.5) = 3 increments -> 100 + 3*20 = 160
        assertThat(calculator.calculate(context)).isEqualByComparingTo("160.00");
    }

    @Test
    @DisplayName("the minimum charge floors freight below it")
    void minimumChargeFloors() {
        Rate rate = PricingTestSupport.rate("RATE1", "0.000", "5.000");
        rate.setBaseRate(new BigDecimal("50.00"));
        rate.setMinimumCharge(new BigDecimal("75.00"));
        PricingContext context = contextFor(List.of(rate), "2.000");

        assertThat(calculator.calculate(context)).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("a gap between two non-adjacent slabs is refused")
    void gapBetweenSlabsRejected() {
        Rate low = PricingTestSupport.rate("LOW", "0.000", "2.000");
        Rate high = PricingTestSupport.rate("HIGH", "5.000", "10.000");
        PricingContext context = contextFor(List.of(low, high), "3.000");

        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("gap")
                .hasMessageContaining("3.000");
    }

    @Test
    void typeAndOrder() {
        assertThat(calculator.type().name()).isEqualTo("FREIGHT");
        assertThat(calculator.order()).isEqualTo(10);
    }

    private PricingContext contextFor(List<Rate> candidates, String chargeableWeight) {
        return PricingTestSupport.contextWithCandidates(candidates, new BigDecimal(chargeableWeight),
                PricingTestSupport.command(new BigDecimal(chargeableWeight)),
                PricingTestSupport.configuration());
    }
}
