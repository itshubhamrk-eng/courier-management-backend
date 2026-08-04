package com.courier.modules.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VolumetricCalculatorTest {

    @Test
    @DisplayName("length x width x height / divisor")
    void computesVolumetricWeight() {
        // 30 x 20 x 10 = 6000 / 5000 = 1.200
        BigDecimal result = VolumetricCalculator.calculate(
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"),
                new BigDecimal(5000));

        assertThat(result).isEqualByComparingTo("1.200");
    }

    @Test
    @DisplayName("any missing dimension yields zero, not a refusal")
    void missingDimensionYieldsZero() {
        assertThat(VolumetricCalculator.calculate(null, new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal(5000))).isEqualByComparingTo("0.000");
        assertThat(VolumetricCalculator.calculate(new BigDecimal("30"), null,
                new BigDecimal("10"), new BigDecimal(5000))).isEqualByComparingTo("0.000");
        assertThat(VolumetricCalculator.calculate(new BigDecimal("30"), new BigDecimal("20"),
                null, new BigDecimal(5000))).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("a smaller divisor yields a larger volumetric weight")
    void smallerDivisorYieldsLargerWeight() {
        BigDecimal result = VolumetricCalculator.calculate(
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"),
                new BigDecimal(4000));

        // 6000 / 4000 = 1.5
        assertThat(result).isEqualByComparingTo("1.500");
    }
}
