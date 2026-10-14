package com.courier.modules.pdp.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A pickup or delivery rate card of one Pickup-Delivery Partner: header values here, per-kg
 * weight slabs in {@link PdpRateSlab}. {@code districtId == null} means every district.
 * What the partner is PAID — independent of what the customer is charged.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pdp_rate_cards")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PdpRateCard extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 20, updatable = false)
    private PdpRateType rateType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)")
    private UUID districtId;

    @Column(name = "min_chargeable_weight", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal minChargeableWeight = BigDecimal.ZERO;

    @Column(name = "mathadi_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal mathadiCharge = BigDecimal.ZERO;

    @Column(name = "oda_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal odaCharge = BigDecimal.ZERO;

    @Column(name = "failed_attempt_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal failedAttemptFee = BigDecimal.ZERO;

    @Column(name = "return_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal returnFee = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PdpRateStatus status = PdpRateStatus.ACTIVE;

    public boolean isActive() {
        return status == PdpRateStatus.ACTIVE;
    }

    public void activate() {
        this.status = PdpRateStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = PdpRateStatus.INACTIVE;
    }

    public boolean coversDate(LocalDate date) {
        return (effectiveFrom == null || !date.isBefore(effectiveFrom))
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    /** True when this card's validity window shares at least one day with {@code [from, to]} (null = open). */
    public boolean overlaps(LocalDate from, LocalDate to) {
        boolean startsBeforeOtherEnds = to == null || effectiveFrom == null || !effectiveFrom.isAfter(to);
        boolean endsAfterOtherStarts = from == null || effectiveTo == null || !effectiveTo.isBefore(from);
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public void applyInvariants() {
        requireNonNegative(minChargeableWeight, "Minimum chargeable weight");
        requireNonNegative(mathadiCharge, "Mathadi charge");
        requireNonNegative(odaCharge, "ODA charge");
        requireNonNegative(failedAttemptFee, "Failed-attempt fee");
        requireNonNegative(returnFee, "Return fee");
        if (rateType == PdpRateType.PICKUP && (returnFee.signum() != 0 || odaCharge.signum() != 0
                || mathadiCharge.signum() != 0)) {
            throw new BusinessRuleException("Mathadi, ODA and return fee apply to delivery rates only.");
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleException("Effective-to date cannot be before the effective-from date.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException(label + " must be zero or more.");
        }
    }
}
