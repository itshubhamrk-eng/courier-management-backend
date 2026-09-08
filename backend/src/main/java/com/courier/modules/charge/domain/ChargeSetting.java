package com.courier.modules.charge.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of a {@link Charge}'s configuration: either a flat {@code FACTOR} value, or a
 * {@code SLAB} banded on KG, KM, or both.
 *
 * <p>Company-owned like its parent — {@code companyId} is denormalised from
 * {@link Charge#getCompanyId()} rather than reached through a join, the same reasoning
 * {@code RolePermission.companyId} documents: it lets the Hibernate filter apply to this
 * table directly on every read. {@code chargeId} is a real, same-module foreign key
 * ({@code RESTRICT}), unlike the cross-module ids this project leaves as plain columns.
 *
 * <p><b>Slab bands are half-open, {@code [from, to)}</b> — the same convention
 * {@code master.domain.WeightSlab} uses, chosen over {@code Rate}'s closed-both-ends
 * convention because a company can carry a KM slab where the boundary is a round
 * number (0/50/100 km) and "does 50 km belong to the 0-50 or the 50-100 band" needs one
 * unambiguous answer. See {@link #overlaps(ChargeSetting)}.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "charge_settings",
        indexes = {
                @Index(name = "idx_charge_settings_charge", columnList = "company_id, charge_id, status"),
                @Index(name = "idx_charge_settings_company", columnList = "company_id")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ChargeSetting extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "charge_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID chargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 20)
    private ChargeType chargeType;

    /** Required when {@link #chargeType} is {@code SLAB}; always {@code null} for {@code FACTOR}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_slab_type", length = 10)
    private ChargeSlabType chargeSlabType;

    @Column(name = "from_km", precision = 12, scale = 3)
    private BigDecimal fromKm;

    @Column(name = "to_km", precision = 12, scale = 3)
    private BigDecimal toKm;

    @Column(name = "from_kg", precision = 12, scale = 3)
    private BigDecimal fromKg;

    @Column(name = "to_kg", precision = 12, scale = 3)
    private BigDecimal toKg;

    @Column(name = "charge_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal chargeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_value_type", nullable = false, length = 20)
    private ChargeValueType chargeValueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false, length = 20)
    private CommissionType commissionType;

    @Column(name = "commission_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return status == ChargeStatus.ACTIVE;
    }

    public void activate() {
        this.status = ChargeStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ChargeStatus.INACTIVE;
    }

    /**
     * True when this setting and {@code other} band the same KG and/or KM range, for the
     * same {@link #chargeSlabType}. Only meaningful for two {@code SLAB} settings sharing
     * both a {@link #chargeId} and a {@link #chargeSlabType} — the caller narrows to that
     * before calling. Half-open on both dimensions: {@code [from, to)}, so a band ending at
     * 50 and one starting at 50 do not overlap, but do if either extends past it.
     */
    public boolean overlaps(ChargeSetting other) {
        if (other == null || chargeType != ChargeType.SLAB || other.chargeType != ChargeType.SLAB
                || chargeSlabType != other.chargeSlabType) {
            return false;
        }
        boolean kmOverlaps = chargeSlabType == ChargeSlabType.KM || chargeSlabType == ChargeSlabType.BOTH
                ? rangesOverlap(fromKm, toKm, other.fromKm, other.toKm)
                : true;
        boolean kgOverlaps = chargeSlabType == ChargeSlabType.KG || chargeSlabType == ChargeSlabType.BOTH
                ? rangesOverlap(fromKg, toKg, other.fromKg, other.toKg)
                : true;
        return kmOverlaps && kgOverlaps;
    }

    private static boolean rangesOverlap(BigDecimal aFrom, BigDecimal aTo, BigDecimal bFrom, BigDecimal bTo) {
        return aFrom.compareTo(bTo) < 0 && bFrom.compareTo(aTo) < 0;
    }

    public void applyInvariants() {
        if (chargeId == null) {
            throw new BusinessRuleException("A charge setting must belong to a charge.");
        }
        if (chargeType == null) {
            throw new BusinessRuleException("A charge type (FACTOR or SLAB) is required.");
        }

        if (chargeType == ChargeType.FACTOR) {
            // Not required for FACTOR; any slab data supplied is ignored rather than
            // rejected, since a caller flipping SLAB -> FACTOR should not have to clear
            // four fields by hand first.
            this.chargeSlabType = null;
            this.fromKm = null;
            this.toKm = null;
            this.fromKg = null;
            this.toKg = null;
        } else {
            if (chargeSlabType == null) {
                throw new BusinessRuleException(
                        "A slab type (BOTH, KG or KM) is required when charge type is SLAB.");
            }
            boolean needsKg = chargeSlabType == ChargeSlabType.KG || chargeSlabType == ChargeSlabType.BOTH;
            boolean needsKm = chargeSlabType == ChargeSlabType.KM || chargeSlabType == ChargeSlabType.BOTH;

            if (needsKg) {
                requireRange(fromKg, toKg, "KG");
            } else {
                this.fromKg = null;
                this.toKg = null;
            }
            if (needsKm) {
                requireRange(fromKm, toKm, "KM");
            } else {
                this.fromKm = null;
                this.toKm = null;
            }
        }

        if (chargeValue == null || chargeValue.signum() < 0) {
            throw new BusinessRuleException("Charge value cannot be negative.");
        }
        if (chargeValueType == null) {
            throw new BusinessRuleException("A charge value type (AMOUNT or PERCENTAGE) is required.");
        }
        requirePercentageInRange(chargeValueType == ChargeValueType.PERCENTAGE, chargeValue, "Charge value");

        if (commissionValue == null || commissionValue.signum() < 0) {
            throw new BusinessRuleException("Commission value cannot be negative.");
        }
        if (commissionType == null) {
            throw new BusinessRuleException("A commission type (AMOUNT or PERCENTAGE) is required.");
        }
        requirePercentageInRange(commissionType == CommissionType.PERCENTAGE, commissionValue, "Commission value");

        if (status == null) {
            this.status = ChargeStatus.ACTIVE;
        }
    }

    private static void requireRange(BigDecimal from, BigDecimal to, String label) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Both from-" + label.toLowerCase() + " and to-" + label.toLowerCase()
                    + " are required for a " + label + " slab.");
        }
        if (from.signum() < 0) {
            throw new BusinessRuleException("from-" + label.toLowerCase() + " cannot be negative.");
        }
        if (to.compareTo(from) <= 0) {
            throw new BusinessRuleException(
                    "to-" + label.toLowerCase() + " must be greater than from-" + label.toLowerCase()
                            + " (the slab is [from, to)).");
        }
    }

    private static void requirePercentageInRange(boolean isPercentage, BigDecimal value, String label) {
        if (isPercentage && value.compareTo(new BigDecimal(100)) > 0) {
            throw new BusinessRuleException(label + " cannot exceed 100 when expressed as a percentage.");
        }
    }
}
