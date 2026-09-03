package com.courier.modules.districtfreight.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistrictLevelFreightTest {

    private DistrictLevelFreight row() {
        DistrictLevelFreight f = new DistrictLevelFreight();
        f.setBranchId(UUID.randomUUID());
        f.setDistrictId(UUID.randomUUID());
        f.setRate1To15(new BigDecimal("10.00"));
        f.setRate16To50(new BigDecimal("8.50"));
        f.setRate51To100(new BigDecimal("8.00"));
        f.setRate101To1000(new BigDecimal("7.50"));
        f.setRate1001To1500(new BigDecimal("6.00"));
        f.setRate1501To2000(new BigDecimal("6.00"));
        f.setOdaCharge(new BigDecimal("250.00"));
        return f;
    }

    @Test
    @DisplayName("applyInvariants requires a branch and a district")
    void requiresBranchAndDistrict() {
        DistrictLevelFreight f = row();
        f.setBranchId(null);
        assertThatThrownBy(f::applyInvariants).isInstanceOf(BusinessRuleException.class);

        DistrictLevelFreight f2 = row();
        f2.setDistrictId(null);
        assertThatThrownBy(f2::applyInvariants).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("applyInvariants rejects a negative rate or ODA charge")
    void rejectsNegativeValues() {
        DistrictLevelFreight f = row();
        f.setRate51To100(new BigDecimal("-0.01"));
        assertThatThrownBy(f::applyInvariants).isInstanceOf(BusinessRuleException.class);

        DistrictLevelFreight f2 = row();
        f2.setOdaCharge(new BigDecimal("-1"));
        assertThatThrownBy(f2::applyInvariants).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("applyInvariants defaults a null status to ACTIVE")
    void defaultsStatus() {
        DistrictLevelFreight f = row();
        f.setStatus(null);
        f.applyInvariants();
        assertThat(f.getStatus()).isEqualTo(DistrictFreightStatus.ACTIVE);
    }

    @Test
    @DisplayName("ratePerKgFor picks the slab that closes on both ends")
    void ratePerKgForPicksTheRightSlab() {
        DistrictLevelFreight f = row();

        assertThat(f.ratePerKgFor(new BigDecimal("1"))).contains(new BigDecimal("10.00"));
        assertThat(f.ratePerKgFor(new BigDecimal("15"))).contains(new BigDecimal("10.00"));
        assertThat(f.ratePerKgFor(new BigDecimal("16"))).contains(new BigDecimal("8.50"));
        assertThat(f.ratePerKgFor(new BigDecimal("50"))).contains(new BigDecimal("8.50"));
        assertThat(f.ratePerKgFor(new BigDecimal("100"))).contains(new BigDecimal("8.00"));
        assertThat(f.ratePerKgFor(new BigDecimal("1000"))).contains(new BigDecimal("7.50"));
        assertThat(f.ratePerKgFor(new BigDecimal("1500"))).contains(new BigDecimal("6.00"));
        assertThat(f.ratePerKgFor(new BigDecimal("2000"))).contains(new BigDecimal("6.00"));
    }

    @Test
    @DisplayName("ratePerKgFor is empty outside the configured [1, 2000] range")
    void ratePerKgForOutOfRange() {
        DistrictLevelFreight f = row();

        assertThat(f.ratePerKgFor(BigDecimal.ZERO)).isEmpty();
        assertThat(f.ratePerKgFor(new BigDecimal("2001"))).isEmpty();
        assertThat(f.ratePerKgFor(null)).isEmpty();
    }

    @Test
    @DisplayName("matchWeightSlab labels the same slab ratePerKgFor's rate came from")
    void matchWeightSlabLabelsTheMatchedSlab() {
        DistrictLevelFreight f = row();

        assertThat(f.matchWeightSlab(new BigDecimal("1"))).contains(
                new DistrictLevelFreight.SlabMatch("1-15 KG", new BigDecimal("10.00")));
        assertThat(f.matchWeightSlab(new BigDecimal("20"))).contains(
                new DistrictLevelFreight.SlabMatch("16-50 KG", new BigDecimal("8.50")));
        assertThat(f.matchWeightSlab(new BigDecimal("60"))).contains(
                new DistrictLevelFreight.SlabMatch("51-100 KG", new BigDecimal("8.00")));
        assertThat(f.matchWeightSlab(new BigDecimal("2000"))).contains(
                new DistrictLevelFreight.SlabMatch("1501-2000 KG", new BigDecimal("6.00")));
        assertThat(f.matchWeightSlab(new BigDecimal("2001"))).isEmpty();
        assertThat(f.matchWeightSlab(null)).isEmpty();
    }
}
