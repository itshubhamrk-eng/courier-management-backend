package com.courier.modules.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeableWeightCalculatorTest {

    @Test
    @DisplayName("actual weight wins when heavier than volumetric")
    void actualWeightWinsWhenHeavier() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("5.000"), new BigDecimal("1.200"));

        assertThat(result).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("volumetric weight wins when the parcel is bulky but light")
    void volumetricWeightWinsWhenBulkier() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("1.000"), new BigDecimal("4.500"));

        assertThat(result).isEqualByComparingTo("4.500");
    }

    @Test
    @DisplayName("equal weights: either is chargeable")
    void equalWeights() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("3.000"), new BigDecimal("3.000"));

        assertThat(result).isEqualByComparingTo("3.000");
    }

    @Test
    @DisplayName("below the company minimum: floors to the minimum")
    void belowMinimumFloorsUp() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("10.000"), new BigDecimal("2.000"), new BigDecimal("15.000"));

        assertThat(result).isEqualByComparingTo("15.000");
    }

    @Test
    @DisplayName("above the company minimum: prices on actual/volumetric, unfloored")
    void aboveMinimumUnaffected() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("16.000"), new BigDecimal("2.000"), new BigDecimal("15.000"));

        assertThat(result).isEqualByComparingTo("16.000");
    }

    @Test
    @DisplayName("null minimum: behaves exactly like the two-arg form")
    void nullMinimumIsNoFloor() {
        BigDecimal result = ChargeableWeightCalculator.calculate(
                new BigDecimal("1.000"), new BigDecimal("4.500"), null);

        assertThat(result).isEqualByComparingTo("4.500");
    }
}
