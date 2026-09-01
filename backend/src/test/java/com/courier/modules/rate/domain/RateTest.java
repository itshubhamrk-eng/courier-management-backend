package com.courier.modules.rate.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The closed weight interval and the invariants a rate must satisfy before it can be
 * saved, plus the fields unique to a rate card row.
 */
class RateTest {

    @Test
    @DisplayName("both the minimum and the maximum are included")
    void closedBothEnds() {
        Rate rate = rate("R1", "1.000", "5.000");

        assertThat(rate.covers(new BigDecimal("1.000"))).isTrue();
        assertThat(rate.covers(new BigDecimal("4.999"))).isTrue();
        assertThat(rate.covers(new BigDecimal("5.000"))).isTrue();
        assertThat(rate.covers(new BigDecimal("5.001"))).isFalse();
        assertThat(rate.covers(new BigDecimal("0.999"))).isFalse();
        assertThat(rate.covers(null)).isFalse();
    }

    @Test
    @DisplayName("weight ranges that share a boundary value still overlap")
    void sharedBoundaryOverlaps() {
        assertThat(rate("A", "0.000", "1.000").overlapsWeightRange(rate("B", "1.000", "5.000")))
                .isTrue();
    }

    @Test
    @DisplayName("weight ranges offset by the smallest increment do not overlap")
    void smallestIncrementOffsetIsFine() {
        assertThat(rate("A", "0.000", "1.000").overlapsWeightRange(rate("B", "1.001", "5.000")))
                .isFalse();
    }

    @Test
    @DisplayName("weight ranges that share any weight overlap, in both directions")
    void overlapIsSymmetric() {
        Rate wide = rate("WIDE", "0.000", "10.000");
        Rate narrow = rate("NARROW", "4.000", "6.000");

        assertThat(wide.overlapsWeightRange(narrow)).isTrue();
        assertThat(narrow.overlapsWeightRange(wide)).isTrue();
    }

    @Test
    @DisplayName("weight ranges in different units never overlap")
    void differentUnitsAreIndependent() {
        Rate kilos = rate("KG", "1.000", "5.000");
        Rate grams = rate("G", "1.000", "5.000");
        grams.setWeightUnit(WeightUnit.GRAM);

        assertThat(kilos.overlapsWeightRange(grams)).isFalse();
    }

    @Test
    @DisplayName("effective window: open-ended, bounded, and outside either edge")
    void effectiveWindow() {
        Rate openEnded = rate("R1", "0.000", "5.000");
        openEnded.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        openEnded.setEffectiveTo(null);
        assertThat(openEnded.coversDate(LocalDate.of(2030, 1, 1))).isTrue();
        assertThat(openEnded.coversDate(LocalDate.of(2025, 12, 31))).isFalse();

        Rate bounded = rate("R2", "0.000", "5.000");
        bounded.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        bounded.setEffectiveTo(LocalDate.of(2026, 12, 31));
        assertThat(bounded.coversDate(LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(bounded.coversDate(LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(bounded.coversDate(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    @DisplayName("a maximum that is not above the minimum is refused")
    void rangeMustBePositive() {
        assertThatThrownBy(() -> rate("BAD", "5.000", "5.000").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than the minimum");
    }

    @Test
    @DisplayName("a negative minimum weight is refused")
    void negativeMinimumWeightRejected() {
        assertThatThrownBy(() -> rate("BAD", "-1.000", "5.000").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("a zero or negative additional weight increment is refused")
    void additionalWeightMustBePositive() {
        Rate rate = rate("BAD", "0.000", "5.000");
        rate.setAdditionalWeight(BigDecimal.ZERO);
        assertThatThrownBy(rate::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("GST outside 0-100 is refused")
    void gstMustBeInRange() {
        Rate over = rate("BAD", "0.000", "5.000");
        over.setGstPercentage(new BigDecimal("101"));
        assertThatThrownBy(over::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("0 and 100");

        Rate negative = rate("BAD", "0.000", "5.000");
        negative.setGstPercentage(new BigDecimal("-1"));
        assertThatThrownBy(negative::applyInvariants)
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("effective-to before effective-from is refused")
    void effectiveToBeforeFromRejected() {
        Rate rate = rate("BAD", "0.000", "5.000");
        rate.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        rate.setEffectiveTo(LocalDate.of(2026, 1, 1));
        assertThatThrownBy(rate::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be before");
    }

    @Test
    @DisplayName("a code is normalised to upper snake case")
    void codeNormalised() {
        assertThat(Rate.normaliseCode("rate one")).isEqualTo("RATE_ONE");
        assertThat(Rate.normaliseCode(null)).isNull();
        assertThat(Rate.normaliseCode("  ")).isNull();
    }

    private static Rate rate(String code, String min, String max) {
        Rate rate = Rate.builder()
                .rateCode(code)
                .rateName(code)
                .routeId(UUID.randomUUID())
                .serviceTypeId(UUID.randomUUID())
                .packageTypeId(UUID.randomUUID())
                .paymentModeId(UUID.randomUUID())
                .minimumWeight(new BigDecimal(min))
                .maximumWeight(new BigDecimal(max))
                .weightUnit(WeightUnit.KG)
                .baseRate(new BigDecimal("100.00"))
                .additionalWeight(new BigDecimal("0.500"))
                .additionalWeightRate(new BigDecimal("20.00"))
                .minimumCharge(BigDecimal.ZERO)
                .fuelSurcharge(BigDecimal.ZERO)
                .handlingCharge(BigDecimal.ZERO)
                .odaCharge(BigDecimal.ZERO)
                .insuranceCharge(BigDecimal.ZERO)
                .gstPercentage(new BigDecimal("18"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(RateStatus.ACTIVE)
                .build();
        return rate;
    }
}
