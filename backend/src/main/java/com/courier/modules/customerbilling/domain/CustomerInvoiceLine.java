package com.courier.modules.customerbilling.domain;

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

/** One shipment on an invoice. A shipment may sit on one live line only. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_invoice_lines")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CustomerInvoiceLine extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "invoice_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID invoiceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "shipment_number", nullable = false, length = 50)
    private String shipmentNumber;

    @Column(name = "booking_date")
    private LocalDate bookingDate;

    @Column(name = "consignee", length = 150)
    private String consignee;

    @Column(name = "destination_pincode", length = 10)
    private String destinationPincode;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "actual_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualWeight;

    @Column(name = "payment_mode", length = 60)
    private String paymentMode;

    @Column(name = "prepaid", nullable = false)
    private boolean prepaid;

    @Column(name = "gst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "dispute_status", length = 10)
    private String disputeStatus;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(name = "dispute_raised_at")
    private java.time.Instant disputeRaisedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dispute_raised_by", columnDefinition = "BINARY(16)")
    private UUID disputeRaisedBy;

    @Column(name = "dispute_resolution", length = 500)
    private String disputeResolution;

    @Column(name = "dispute_resolved_at")
    private java.time.Instant disputeResolvedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "dispute_resolved_by", columnDefinition = "BINARY(16)")
    private UUID disputeResolvedBy;

    public boolean isDisputeOpen() {
        return "OPEN".equals(disputeStatus);
    }

    public void raiseDispute(String reason, UUID actor) {
        if (isDisputeOpen()) {
            throw new com.courier.shared.exception.BusinessRuleException("This shipment already has an open dispute.");
        }
        this.disputeStatus = "OPEN";
        this.disputeReason = reason;
        this.disputeRaisedAt = java.time.Instant.now();
        this.disputeRaisedBy = actor;
        this.disputeResolution = null;
        this.disputeResolvedAt = null;
        this.disputeResolvedBy = null;
    }

    public void resolveDispute(String resolution, UUID actor) {
        if (!isDisputeOpen()) {
            throw new com.courier.shared.exception.BusinessRuleException("This shipment has no open dispute.");
        }
        this.disputeStatus = "RESOLVED";
        this.disputeResolution = resolution;
        this.disputeResolvedAt = java.time.Instant.now();
        this.disputeResolvedBy = actor;
    }
}
