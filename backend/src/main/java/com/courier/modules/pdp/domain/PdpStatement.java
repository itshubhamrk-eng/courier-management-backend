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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A billing statement of one partner for a period. Paid is a manual mark — it never touches a wallet. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pdp_statements")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PdpStatement extends CompanyOwnedEntity {

    @Column(name = "statement_no", nullable = false, length = 30, updatable = false)
    private String statementNo;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "period_from", nullable = false, updatable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false, updatable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PdpStatementStatus status = PdpStatementStatus.DRAFT;

    @Column(name = "pickup_count", nullable = false)
    private int pickupCount;

    @Column(name = "delivery_count", nullable = false)
    private int deliveryCount;

    @Column(name = "pickup_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal pickupTotal = BigDecimal.ZERO;

    @Column(name = "delivery_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal deliveryTotal = BigDecimal.ZERO;

    @Column(name = "net_payable", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "payment_ref", length = 100)
    private String paymentRef;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public void approve() {
        if (status != PdpStatementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft statement can be approved.");
        }
        status = PdpStatementStatus.APPROVED;
        approvedAt = Instant.now();
    }

    public void markPaid(LocalDate on, String reference) {
        if (status != PdpStatementStatus.APPROVED) {
            throw new BusinessRuleException("Only an approved statement can be marked paid.");
        }
        if (on == null) {
            throw new BusinessRuleException("A payment date is required.");
        }
        status = PdpStatementStatus.PAID;
        paidOn = on;
        paymentRef = reference == null || reference.isBlank() ? null : reference.trim();
    }
}
