package com.courier.modules.pdp.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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
 * What a Pickup-Delivery Partner is owed for one job. Accrued only — never credited to a
 * wallet. All pricing inputs are snapshotted so editing a rate card never rewrites history.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pdp_earnings")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PdpEarning extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_type", nullable = false, length = 20, updatable = false)
    private PdpEarningType earningType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "source_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "reference_no", nullable = false, length = 50, updatable = false)
    private String referenceNo;

    @Column(name = "earned_on", nullable = false)
    private LocalDate earnedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PdpEarningStatus status = PdpEarningStatus.ACCRUED;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)")
    private UUID districtId;

    @Column(name = "actual_weight_kg", precision = 12, scale = 3)
    private BigDecimal actualWeightKg;

    @Column(name = "chargeable_weight_kg", precision = 12, scale = 3)
    private BigDecimal chargeableWeightKg;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "rate_id", columnDefinition = "BINARY(16)")
    private UUID rateId;

    @Column(name = "rate_per_kg", precision = 12, scale = 2)
    private BigDecimal ratePerKg;

    @Column(name = "weight_charge", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal weightCharge = BigDecimal.ZERO;

    @Column(name = "mathadi_charge", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal mathadiCharge = BigDecimal.ZERO;

    @Column(name = "oda_charge", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal odaCharge = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "remark", length = 255)
    private String remark;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "statement_id", columnDefinition = "BINARY(16)")
    private UUID statementId;
}
