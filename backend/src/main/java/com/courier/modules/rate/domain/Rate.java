package com.courier.modules.rate.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A company's rate card row: one weight slab, for one {@code Route} + service type +
 * package type + payment mode combination.
 *
 * <p>Company-owned, like every operational record. {@code rateCode} is unique within the
 * company (including against soft-deleted rows — a shipment may end up quoting it) and
 * immutable once assigned, the same treatment {@code Customer.customerCode} and every
 * master code get.
 *
 * <p>{@code routeId}, {@code serviceTypeId}, {@code packageTypeId} and
 * {@code paymentModeId} are plain {@code UUID} columns with no foreign key — they name
 * rows in {@code com.courier.modules.master}'s own tables, a different module, validated
 * in {@code RateServiceImpl} through that module's application service interfaces rather
 * than a physical FK. Same treatment {@code customer_addresses}' geography ids and
 * {@code branches.manager_id} already get.
 *
 * <p><b>Weight slabs are closed on both ends, {@code [minimumWeight, maximumWeight]}</b> —
 * see {@link #covers(BigDecimal)} and {@link #overlapsWeightRange(Rate)}. A boundary value
 * belongs to whichever slab's maximum it hits, but two slabs that merely touch at that
 * value still count as overlapping — adjacent slabs must be offset by the smallest
 * representable increment (e.g. {@code 10.000}/{@code 10.001}), not share the boundary.
 * No two <i>active</i> rates for the same Route + Service Type + Package Type + Payment
 * Mode may cover the same weight; that rule cannot live in a database constraint (MySQL
 * has no exclusion constraint), so it lives in {@code RateServiceImpl} and runs on
 * activation too, not only on save.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "rate_master",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rate_master_company_code",
                        columnNames = {"company_id", "rate_code"})
        },
        indexes = {
                @Index(name = "idx_rate_master_combo",
                        columnList = "company_id, route_id, service_type_id, package_type_id, "
                                + "payment_mode_id, status"),
                @Index(name = "idx_rate_master_status", columnList = "company_id, status"),
                @Index(name = "idx_rate_master_effective",
                        columnList = "company_id, effective_from, effective_to")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Rate extends CompanyOwnedEntity {

    @Column(name = "rate_code", nullable = false, updatable = false, length = 50)
    private String rateCode;

    @Column(name = "rate_name", nullable = false, length = 150)
    private String rateName;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "route_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID routeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "service_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID serviceTypeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "package_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID packageTypeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_mode_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID paymentModeId;

    @Column(name = "minimum_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal minimumWeight;

    @Column(name = "maximum_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal maximumWeight;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "weight_unit", nullable = false, length = 10)
    @Builder.Default
    private WeightUnit weightUnit = WeightUnit.KG;

    @Column(name = "base_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseRate;

    /** The overage increment: extra weight beyond {@link #maximumWeight} is billed per
     * this much of it, at {@link #additionalWeightRate}. */
    @Column(name = "additional_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal additionalWeight;

    @Column(name = "additional_weight_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal additionalWeightRate;

    @Column(name = "minimum_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal minimumCharge = BigDecimal.ZERO;

    @Column(name = "fuel_surcharge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fuelSurcharge = BigDecimal.ZERO;

    @Column(name = "handling_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal handlingCharge = BigDecimal.ZERO;

    @Column(name = "oda_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal odaCharge = BigDecimal.ZERO;

    @Column(name = "insurance_charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal insuranceCharge = BigDecimal.ZERO;

    /** {@code [0, 100]}. */
    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstPercentage = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** {@code null} means open-ended. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RateStatus status = RateStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public static String normaliseCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase().replace(' ', '_');
    }

    public boolean isActive() {
        return status == RateStatus.ACTIVE;
    }

    public void activate() {
        this.status = RateStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = RateStatus.INACTIVE;
    }

    /** True when {@code weight} falls in {@code [minimumWeight, maximumWeight]}. */
    public boolean covers(BigDecimal weight) {
        return weight != null
                && weight.compareTo(minimumWeight) >= 0
                && weight.compareTo(maximumWeight) <= 0;
    }

    /**
     * True when this rate and {@code other} price the same weight, within the same unit.
     * Closed on both sides: a 0-10 slab and a 10-20 slab <b>do</b> overlap — both would
     * match weight 10 — so adjacent slabs must be offset by the smallest representable
     * increment instead of sharing the boundary. Does not compare Route / Service Type /
     * Package Type / Payment Mode — the caller narrows to one combination first.
     */
    public boolean overlapsWeightRange(Rate other) {
        return other != null
                && weightUnit == other.weightUnit
                && minimumWeight.compareTo(other.maximumWeight) <= 0
                && other.minimumWeight.compareTo(maximumWeight) <= 0;
    }

    /** True when {@code date} falls within {@link #effectiveFrom}/{@link #effectiveTo}. */
    public boolean coversDate(LocalDate date) {
        if (date == null || effectiveFrom == null) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }

    public void applyInvariants() {
        this.rateCode = normaliseCode(rateCode);
        this.rateName = rateName == null ? null : rateName.trim();

        if (rateCode == null || rateCode.isBlank()) {
            throw new BusinessRuleException("A rate code is required.");
        }
        if (rateName == null || rateName.isBlank()) {
            throw new BusinessRuleException("A rate name is required.");
        }
        if (routeId == null) {
            throw new BusinessRuleException("A rate must belong to a route.");
        }
        if (serviceTypeId == null || packageTypeId == null || paymentModeId == null) {
            throw new BusinessRuleException(
                    "A rate needs a service type, a package type and a payment mode.");
        }
        if (minimumWeight == null || maximumWeight == null) {
            throw new BusinessRuleException("A rate needs both a minimum and a maximum weight.");
        }
        if (minimumWeight.signum() < 0) {
            throw new BusinessRuleException("Minimum weight cannot be negative.");
        }
        if (maximumWeight.compareTo(minimumWeight) <= 0) {
            throw new BusinessRuleException(
                    "Maximum weight must be greater than the minimum (the slab is [min, max]).");
        }
        if (weightUnit == null) {
            this.weightUnit = WeightUnit.KG;
        }
        if (baseRate == null || baseRate.signum() < 0) {
            throw new BusinessRuleException("Base rate cannot be negative.");
        }
        if (additionalWeight == null || additionalWeight.signum() <= 0) {
            throw new BusinessRuleException("Additional weight increment must be greater than zero.");
        }
        if (additionalWeightRate == null || additionalWeightRate.signum() < 0) {
            throw new BusinessRuleException("Additional weight rate cannot be negative.");
        }
        requireNonNegative(minimumCharge, "Minimum charge");
        requireNonNegative(fuelSurcharge, "Fuel surcharge");
        requireNonNegative(handlingCharge, "Handling charge");
        requireNonNegative(odaCharge, "ODA charge");
        requireNonNegative(insuranceCharge, "Insurance charge");
        if (gstPercentage == null || gstPercentage.signum() < 0
                || gstPercentage.compareTo(new BigDecimal(100)) > 0) {
            throw new BusinessRuleException("GST percentage must be between 0 and 100.");
        }
        if (effectiveFrom == null) {
            throw new BusinessRuleException("Effective-from date is required.");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleException("Effective-to date cannot be before effective-from.");
        }
        if (status == null) {
            this.status = RateStatus.ACTIVE;
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException(label + " cannot be negative.");
        }
    }
}
