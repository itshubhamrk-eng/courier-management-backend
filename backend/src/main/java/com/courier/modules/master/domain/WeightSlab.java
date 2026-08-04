package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * A weight band the rate master prices against.
 *
 * <p>The interval is <b>half-open, {@code [min, max)}</b>. A parcel of exactly 1 kg falls
 * in the 1–5 slab, not the 0–1 one. Stating that here and enforcing it in
 * {@link #covers(BigDecimal)} is the whole point of the class: "which slab is 1 kg in"
 * has to have one answer, and inclusive-both-ends slabs give it two.
 *
 * <p>The service additionally refuses to let two <i>active</i> slabs of the same unit
 * overlap. That rule cannot live in the database — MySQL has no exclusion constraint — so
 * it lives in {@code WeightSlabServiceImpl} and is covered by tests.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_weight_slabs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_weight_slabs_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_weight_slabs_name",
                        columnNames = {"company_id", "name"})
        },
        indexes = {
                @Index(name = "idx_master_weight_slabs_status", columnList = "company_id, status"),
                @Index(name = "idx_master_weight_slabs_range",
                        columnList = "company_id, weight_unit, min_weight, max_weight")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class WeightSlab extends MasterDataEntity {

    @Column(name = "min_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal minWeight;

    @Column(name = "max_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal maxWeight;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "weight_unit", nullable = false, length = 10)
    private WeightUnit weightUnit = WeightUnit.KG;

    /** True when {@code weight} falls in {@code [min, max)}. */
    public boolean covers(BigDecimal weight) {
        return weight != null
                && weight.compareTo(minWeight) >= 0
                && weight.compareTo(maxWeight) < 0;
    }

    /**
     * True when this slab and {@code other} share any weight. Half-open on both sides, so
     * a 0–1 slab and a 1–5 slab do <i>not</i> overlap — they are exactly adjacent, which
     * is what a sane tariff looks like.
     */
    public boolean overlaps(WeightSlab other) {
        return other != null
                && weightUnit == other.weightUnit
                && minWeight.compareTo(other.maxWeight) < 0
                && other.minWeight.compareTo(maxWeight) < 0;
    }

    @Override
    protected void applySpecificInvariants() {
        if (minWeight == null || maxWeight == null) {
            throw new BusinessRuleException("A weight slab needs both a minimum and a maximum.");
        }
        if (minWeight.signum() < 0) {
            throw new BusinessRuleException("Minimum weight cannot be negative.");
        }
        if (maxWeight.compareTo(minWeight) <= 0) {
            throw new BusinessRuleException(
                    "Maximum weight must be greater than the minimum (the slab is [min, max)).");
        }
        if (weightUnit == null) {
            this.weightUnit = WeightUnit.KG;
        }
    }
}
