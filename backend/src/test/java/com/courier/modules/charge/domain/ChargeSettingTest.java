package com.courier.modules.charge.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The conditional shape a charge setting must satisfy — FACTOR vs. SLAB x {KG, KM,
 * BOTH} — and the half-open overlap rule.
 */
class ChargeSettingTest {

    @Test
    @DisplayName("FACTOR needs no slab fields, and any supplied are cleared")
    void factorIgnoresSlabFields() {
        ChargeSetting setting = base(ChargeType.FACTOR, null)
                .fromKg(new BigDecimal("1")).toKg(new BigDecimal("2"))
                .chargeSlabType(ChargeSlabType.KG)
                .build();

        setting.applyInvariants();

        assertThat(setting.getChargeSlabType()).isNull();
        assertThat(setting.getFromKg()).isNull();
        assertThat(setting.getToKg()).isNull();
    }

    @Test
    @DisplayName("SLAB with no slab type is refused")
    void slabRequiresSlabType() {
        ChargeSetting setting = base(ChargeType.SLAB, null).build();
        assertThatThrownBy(setting::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("slab type");
    }

    @Test
    @DisplayName("SLAB + KG requires fromKg/toKg and leaves fromKm/toKm null")
    void slabKgRequiresKgRange() {
        ChargeSetting missing = base(ChargeType.SLAB, ChargeSlabType.KG).build();
        assertThatThrownBy(missing::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("KG slab");

        ChargeSetting valid = base(ChargeType.SLAB, ChargeSlabType.KG)
                .fromKg(new BigDecimal("0")).toKg(new BigDecimal("5"))
                .fromKm(new BigDecimal("10")).toKm(new BigDecimal("20"))
                .build();
        valid.applyInvariants();
        assertThat(valid.getFromKm()).isNull();
        assertThat(valid.getToKm()).isNull();
    }

    @Test
    @DisplayName("SLAB + KM requires fromKm/toKm and leaves fromKg/toKg null")
    void slabKmRequiresKmRange() {
        ChargeSetting missing = base(ChargeType.SLAB, ChargeSlabType.KM).build();
        assertThatThrownBy(missing::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("KM slab");

        ChargeSetting valid = base(ChargeType.SLAB, ChargeSlabType.KM)
                .fromKm(new BigDecimal("0")).toKm(new BigDecimal("50"))
                .build();
        valid.applyInvariants();
        assertThat(valid.getFromKg()).isNull();
    }

    @Test
    @DisplayName("SLAB + BOTH requires all four bounds")
    void slabBothRequiresAllFour() {
        ChargeSetting missingKg = base(ChargeType.SLAB, ChargeSlabType.BOTH)
                .fromKm(new BigDecimal("0")).toKm(new BigDecimal("50"))
                .build();
        assertThatThrownBy(missingKg::applyInvariants).isInstanceOf(BusinessRuleException.class);

        ChargeSetting valid = base(ChargeType.SLAB, ChargeSlabType.BOTH)
                .fromKm(new BigDecimal("0")).toKm(new BigDecimal("50"))
                .fromKg(new BigDecimal("0")).toKg(new BigDecimal("5"))
                .build();
        valid.applyInvariants();
    }

    @Test
    @DisplayName("to must be strictly greater than from")
    void toMustExceedFrom() {
        ChargeSetting setting = base(ChargeType.SLAB, ChargeSlabType.KG)
                .fromKg(new BigDecimal("5")).toKg(new BigDecimal("5"))
                .build();
        assertThatThrownBy(setting::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than from-kg");
    }

    @Test
    @DisplayName("a negative charge value is refused")
    void negativeChargeValueRejected() {
        ChargeSetting setting = base(ChargeType.FACTOR, null).chargeValue(new BigDecimal("-1")).build();
        assertThatThrownBy(setting::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Charge value cannot be negative");
    }

    @Test
    @DisplayName("a percentage charge value over 100 is refused")
    void percentageChargeValueCapped() {
        ChargeSetting setting = base(ChargeType.FACTOR, null)
                .chargeValue(new BigDecimal("150")).chargeValueType(ChargeValueType.PERCENTAGE)
                .build();
        assertThatThrownBy(setting::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    @DisplayName("a percentage commission value over 100 is refused")
    void percentageCommissionValueCapped() {
        ChargeSetting setting = base(ChargeType.FACTOR, null)
                .commissionValue(new BigDecimal("101")).commissionType(CommissionType.PERCENTAGE)
                .build();
        assertThatThrownBy(setting::applyInvariants)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    // ------------------------------------------------------------------ overlap

    @Test
    @DisplayName("KG slabs overlap only when their bands share weight, half-open")
    void kgOverlapHalfOpen() {
        ChargeSetting a = kg("0", "5");
        ChargeSetting b = kg("5", "10");
        ChargeSetting c = kg("4", "8");

        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(c)).isTrue();
        assertThat(b.overlaps(c)).isTrue();
    }

    @Test
    @DisplayName("KM and KG slabs of the same charge never overlap each other")
    void differentSlabTypesNeverOverlap() {
        ChargeSetting kg = kg("0", "5");
        ChargeSetting km = km("0", "5");
        assertThat(kg.overlaps(km)).isFalse();
    }

    @Test
    @DisplayName("FACTOR settings never overlap")
    void factorNeverOverlaps() {
        ChargeSetting a = base(ChargeType.FACTOR, null).build();
        ChargeSetting b = base(ChargeType.FACTOR, null).build();
        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    @DisplayName("BOTH overlaps only when both dimensions overlap")
    void bothRequiresBothDimensionsToOverlap() {
        ChargeSetting a = both("0", "50", "0", "5");
        ChargeSetting sameKmDifferentKg = both("0", "50", "5", "10");
        ChargeSetting differentKmSameKg = both("50", "100", "0", "5");
        ChargeSetting overlapsBoth = both("25", "75", "2", "7");

        assertThat(a.overlaps(sameKmDifferentKg)).isFalse();
        assertThat(a.overlaps(differentKmSameKg)).isFalse();
        assertThat(a.overlaps(overlapsBoth)).isTrue();
    }

    // -------------------------------------------------------------------- helpers

    private static ChargeSetting.ChargeSettingBuilder base(ChargeType type, ChargeSlabType slabType) {
        return ChargeSetting.builder()
                .chargeId(UUID.randomUUID())
                .chargeType(type)
                .chargeSlabType(slabType)
                .chargeValue(new BigDecimal("100"))
                .chargeValueType(ChargeValueType.AMOUNT)
                .commissionType(CommissionType.AMOUNT)
                .commissionValue(BigDecimal.ZERO)
                .status(ChargeStatus.ACTIVE);
    }

    private static ChargeSetting kg(String from, String to) {
        ChargeSetting s = base(ChargeType.SLAB, ChargeSlabType.KG)
                .fromKg(new BigDecimal(from)).toKg(new BigDecimal(to)).build();
        s.applyInvariants();
        return s;
    }

    private static ChargeSetting km(String from, String to) {
        ChargeSetting s = base(ChargeType.SLAB, ChargeSlabType.KM)
                .fromKm(new BigDecimal(from)).toKm(new BigDecimal(to)).build();
        s.applyInvariants();
        return s;
    }

    private static ChargeSetting both(String fromKm, String toKm, String fromKg, String toKg) {
        ChargeSetting s = base(ChargeType.SLAB, ChargeSlabType.BOTH)
                .fromKm(new BigDecimal(fromKm)).toKm(new BigDecimal(toKm))
                .fromKg(new BigDecimal(fromKg)).toKg(new BigDecimal(toKg)).build();
        s.applyInvariants();
        return s;
    }
}
