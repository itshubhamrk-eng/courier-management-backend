package com.courier.modules.freight.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * A company's freight pricing grid cell: one distance range x one weight range, carrying
 * a multiplier ("factor"). Freight for a shipment is {@code factor * weight} for whichever
 * cell covers its resolved distance and weight — see {@code FreightFactorServiceImpl
 * .calculate}.
 *
 * <p>Company-owned, like every operational record. Deliberately independent of
 * {@code com.courier.modules.rate}/{@code com.courier.modules.pricing} — this is a
 * separate pricing mechanism, not a Route-based one; nothing here references a Route,
 * Service Type, Package Type or Payment Mode.
 *
 * <p><b>Both ranges are closed on both ends</b> — {@code [fromKm, toKm]} and
 * {@code [fromWeight, toWeight]}, the same convention {@code rate.domain.Rate}'s weight
 * slab uses. A boundary value belongs to whichever cell's {@code to} it hits, but two cells
 * that merely touch at that value still count as overlapping — adjacent cells must be
 * offset by the smallest representable increment (e.g. {@code 10.000}/{@code 10.001}), not
 * share the boundary. No two <i>active</i> cells may cover the same (distance, weight)
 * point; since this is a 2D grid, a conflict requires <b>both</b> the distance ranges and
 * the weight ranges to overlap (see {@link #overlaps(FreightFactor)}) — two cells can share
 * a distance band as long as their weight bands don't collide, and vice versa.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "freight_factor",
        indexes = {
                @Index(name = "idx_freight_factor_status", columnList = "company_id, status"),
                @Index(name = "idx_freight_factor_range",
                        columnList = "company_id, status, from_km, from_weight")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class FreightFactor extends CompanyOwnedEntity {

    @Column(name = "from_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal fromKm;

    @Column(name = "to_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal toKm;

    @Column(name = "from_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal fromWeight;

    @Column(name = "to_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal toWeight;

    @Column(name = "factor", nullable = false, precision = 19, scale = 4)
    private BigDecimal factor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FreightFactorStatus status = FreightFactorStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return status == FreightFactorStatus.ACTIVE;
    }

    public void activate() {
        this.status = FreightFactorStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = FreightFactorStatus.INACTIVE;
    }

    /** True when {@code km} falls in {@code [fromKm, toKm]}. */
    public boolean coversDistance(BigDecimal km) {
        return km != null && km.compareTo(fromKm) >= 0 && km.compareTo(toKm) <= 0;
    }

    /** True when {@code weight} falls in {@code [fromWeight, toWeight]}. */
    public boolean coversWeight(BigDecimal weight) {
        return weight != null && weight.compareTo(fromWeight) >= 0 && weight.compareTo(toWeight) <= 0;
    }

    /**
     * True when this cell and {@code other} could both match the same (distance, weight)
     * point — a conflict needs the distance ranges <b>and</b> the weight ranges to overlap
     * simultaneously, since this is a 2D grid, not a single-dimension slab like
     * {@code Rate.overlapsWeightRange}. Both ends are inclusive, so two cells that merely
     * touch at a shared boundary value (one's {@code to} equal to the other's {@code from})
     * still conflict — that value would match both.
     */
    public boolean overlaps(FreightFactor other) {
        if (other == null) {
            return false;
        }
        boolean distanceOverlaps = fromKm.compareTo(other.toKm) <= 0 && other.fromKm.compareTo(toKm) <= 0;
        boolean weightOverlaps = fromWeight.compareTo(other.toWeight) <= 0
                && other.fromWeight.compareTo(toWeight) <= 0;
        return distanceOverlaps && weightOverlaps;
    }

    public void applyInvariants() {
        if (fromKm == null || toKm == null) {
            throw new BusinessRuleException("A freight factor needs both a from-km and a to-km.");
        }
        if (fromKm.signum() < 0) {
            throw new BusinessRuleException("From-km cannot be negative.");
        }
        if (toKm.compareTo(fromKm) <= 0) {
            throw new BusinessRuleException(
                    "To-km must be greater than from-km (the range is [from, to]).");
        }
        if (fromWeight == null || toWeight == null) {
            throw new BusinessRuleException("A freight factor needs both a from-weight and a to-weight.");
        }
        if (fromWeight.signum() < 0) {
            throw new BusinessRuleException("From-weight cannot be negative.");
        }
        if (toWeight.compareTo(fromWeight) <= 0) {
            throw new BusinessRuleException(
                    "To-weight must be greater than from-weight (the range is [from, to]).");
        }
        if (factor == null || factor.signum() <= 0) {
            throw new BusinessRuleException("Factor must be greater than zero.");
        }
        if (status == null) {
            this.status = FreightFactorStatus.ACTIVE;
        }
    }
}
