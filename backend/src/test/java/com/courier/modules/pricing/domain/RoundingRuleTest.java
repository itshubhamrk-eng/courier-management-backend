package com.courier.modules.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RoundingRuleTest {

    @Test
    void none_keepsTwoDecimals() {
        assertThat(RoundingRule.NONE.apply(new BigDecimal("128.37"))).isEqualByComparingTo("128.37");
    }

    @Test
    void nearestOne_roundsHalfUp() {
        assertThat(RoundingRule.NEAREST_ONE.apply(new BigDecimal("128.30"))).isEqualByComparingTo("128.00");
        assertThat(RoundingRule.NEAREST_ONE.apply(new BigDecimal("128.50"))).isEqualByComparingTo("129.00");
    }

    @Test
    void nearestFive_roundsToTheClosestMultiple() {
        assertThat(RoundingRule.NEAREST_FIVE.apply(new BigDecimal("128.30"))).isEqualByComparingTo("130.00");
        assertThat(RoundingRule.NEAREST_FIVE.apply(new BigDecimal("122.00"))).isEqualByComparingTo("120.00");
    }

    @Test
    void nearestTen_roundsToTheClosestMultiple() {
        assertThat(RoundingRule.NEAREST_TEN.apply(new BigDecimal("124.00"))).isEqualByComparingTo("120.00");
        assertThat(RoundingRule.NEAREST_TEN.apply(new BigDecimal("126.00"))).isEqualByComparingTo("130.00");
    }
}
