package com.courier.modules.freight.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The closed distance/weight intervals and the 2D overlap rule a freight factor cell
 * must satisfy — the same shape {@code RateTest} exercises for a single-dimension slab,
 * extended to two independent dimensions.
 */
class FreightFactorTest {

    @Test
    @DisplayName("both the minimum and the maximum are included, on both axes")
    void closedBothEnds() {
        FreightFactor cell = cell("0.000", "100.000", "0.000", "10.000", "5.00");

        assertThat(cell.coversDistance(new BigDecimal("0.000"))).isTrue();
        assertThat(cell.coversDistance(new BigDecimal("99.999"))).isTrue();
        assertThat(cell.coversDistance(new BigDecimal("100.000"))).isTrue();
        assertThat(cell.coversDistance(new BigDecimal("100.001"))).isFalse();
        assertThat(cell.coversDistance(null)).isFalse();

        assertThat(cell.coversWeight(new BigDecimal("0.000"))).isTrue();
        assertThat(cell.coversWeight(new BigDecimal("9.999"))).isTrue();
        assertThat(cell.coversWeight(new BigDecimal("10.000"))).isTrue();
        assertThat(cell.coversWeight(new BigDecimal("10.001"))).isFalse();
        assertThat(cell.coversWeight(null)).isFalse();
    }

    @Test
    @DisplayName("a conflict needs both the distance range and the weight range to overlap")
    void overlapNeedsBothDimensions() {
        FreightFactor base = cell("0.000", "100.000", "0.000", "10.000", "5.00");

        // Same distance band, disjoint weight band -> no conflict.
        FreightFactor disjointWeight = cell("0.000", "100.000", "10.001", "20.000", "5.00");
        assertThat(base.overlaps(disjointWeight)).isFalse();

        // Same weight band, disjoint distance band -> no conflict.
        FreightFactor disjointDistance = cell("100.001", "200.000", "0.000", "10.000", "5.00");
        assertThat(base.overlaps(disjointDistance)).isFalse();

        // Both bands overlap -> conflict, symmetric.
        FreightFactor bothOverlap = cell("50.000", "150.000", "5.000", "15.000", "5.00");
        assertThat(base.overlaps(bothOverlap)).isTrue();
        assertThat(bothOverlap.overlaps(base)).isTrue();
    }

    @Test
    @DisplayName("ranges that share a boundary value still overlap, on either axis")
    void sharedBoundaryOverlaps() {
        FreightFactor base = cell("0.000", "100.000", "0.000", "10.000", "5.00");
        FreightFactor touchingDistance = cell("100.000", "200.000", "0.000", "10.000", "5.00");
        FreightFactor touchingWeight = cell("0.000", "100.000", "10.000", "20.000", "5.00");

        assertThat(base.overlaps(touchingDistance)).isTrue();
        assertThat(base.overlaps(touchingWeight)).isTrue();
    }

    @Test
    @DisplayName("ranges offset by the smallest increment do not overlap")
    void smallestIncrementOffsetIsFine() {
        FreightFactor base = cell("0.000", "100.000", "0.000", "10.000", "5.00");
        FreightFactor nextDistance = cell("100.001", "200.000", "0.000", "10.000", "5.00");
        FreightFactor nextWeight = cell("0.000", "100.000", "10.001", "20.000", "5.00");

        assertThat(base.overlaps(nextDistance)).isFalse();
        assertThat(base.overlaps(nextWeight)).isFalse();
    }

    @Test
    @DisplayName("a to-km not above from-km is refused")
    void kmRangeMustBePositive() {
        assertThatThrownBy(() -> cell("100.000", "100.000", "0.000", "10.000", "5.00").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than from-km");
    }

    @Test
    @DisplayName("a to-weight not above from-weight is refused")
    void weightRangeMustBePositive() {
        assertThatThrownBy(() -> cell("0.000", "100.000", "10.000", "10.000", "5.00").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than from-weight");
    }

    @Test
    @DisplayName("a negative from-km is refused")
    void negativeFromKmRejected() {
        assertThatThrownBy(() -> cell("-1.000", "100.000", "0.000", "10.000", "5.00").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("a zero or negative factor is refused")
    void factorMustBePositive() {
        assertThatThrownBy(() -> cell("0.000", "100.000", "0.000", "10.000", "0.00").applyInvariants())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    private static FreightFactor cell(String fromKm, String toKm, String fromWeight, String toWeight,
                                      String factor) {
        return FreightFactor.builder()
                .fromKm(new BigDecimal(fromKm))
                .toKm(new BigDecimal(toKm))
                .fromWeight(new BigDecimal(fromWeight))
                .toWeight(new BigDecimal(toWeight))
                .factor(new BigDecimal(factor))
                .status(FreightFactorStatus.ACTIVE)
                .build();
    }
}
