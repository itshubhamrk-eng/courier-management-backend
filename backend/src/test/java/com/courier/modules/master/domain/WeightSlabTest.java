package com.courier.modules.master.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The half-open interval, which is the whole point of the class.
 *
 * <p>If a boundary weight fell in two slabs, the rate engine would price two identical
 * shipments differently depending on which row the database returned first — a bug that
 * surfaces as a customer complaint months later rather than as an error.
 */
class WeightSlabTest {

    @Test
    @DisplayName("the minimum is included and the maximum excluded")
    void halfOpen() {
        WeightSlab slab = slab("SLAB_1_5", "1.000", "5.000");

        assertThat(slab.covers(new BigDecimal("1.000"))).isTrue();
        assertThat(slab.covers(new BigDecimal("4.999"))).isTrue();
        assertThat(slab.covers(new BigDecimal("5.000"))).isFalse();
        assertThat(slab.covers(new BigDecimal("0.999"))).isFalse();
        assertThat(slab.covers(null)).isFalse();
    }

    @Test
    @DisplayName("exactly adjacent slabs do not overlap")
    void adjacentIsFine() {
        // 0-1 and 1-5 is what a sane tariff looks like; refusing it would make every
        // tariff impossible to enter.
        assertThat(slab("A", "0.000", "1.000").overlaps(slab("B", "1.000", "5.000"))).isFalse();
        assertThat(slab("B", "1.000", "5.000").overlaps(slab("A", "0.000", "1.000"))).isFalse();
    }

    @Test
    @DisplayName("slabs that share any weight overlap, in both directions")
    void overlapIsSymmetric() {
        WeightSlab wide = slab("WIDE", "0.000", "10.000");
        WeightSlab narrow = slab("NARROW", "4.000", "6.000");

        assertThat(wide.overlaps(narrow)).isTrue();
        assertThat(narrow.overlaps(wide)).isTrue();
    }

    @Test
    @DisplayName("slabs in different units never overlap")
    void differentUnitsAreIndependent() {
        // A 1-5 kg band and a 1-5 g band describe different weights entirely, so the
        // comparison is meaningless rather than conflicting.
        WeightSlab kilos = slab("KG", "1.000", "5.000");
        WeightSlab grams = slab("G", "1.000", "5.000");
        grams.setWeightUnit(WeightUnit.GRAM);

        assertThat(kilos.overlaps(grams)).isFalse();
    }

    @Test
    @DisplayName("a maximum that is not above the minimum is refused")
    void rangeMustBePositive() {
        assertThatThrownBy(() -> slab("BAD", "5.000", "5.000").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than the minimum");

        assertThatThrownBy(() -> slab("BAD", "5.000", "1.000").applyInvariants())
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a negative minimum is refused and a missing bound is refused")
    void boundsAreValidated() {
        assertThatThrownBy(() -> slab("BAD", "-1.000", "5.000").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");

        WeightSlab missing = slab("BAD", "1.000", "5.000");
        missing.setMaxWeight(null);
        assertThatThrownBy(missing::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("minimum and a maximum");
    }

    @Test
    @DisplayName("a null unit defaults to kilograms")
    void unitDefaults() {
        WeightSlab slab = slab("SLAB", "1.000", "5.000");
        slab.setWeightUnit(null);
        slab.applyInvariants();

        assertThat(slab.getWeightUnit()).isEqualTo(WeightUnit.KG);
    }

    private static WeightSlab slab(String code, String min, String max) {
        WeightSlab slab = new WeightSlab();
        slab.setCode(code);
        slab.setName(code);
        slab.setMinWeight(new BigDecimal(min));
        slab.setMaxWeight(new BigDecimal(max));
        slab.setWeightUnit(WeightUnit.KG);
        return slab;
    }
}
