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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.shared.exception.BusinessRuleException;

/**
 * A bill to one business customer for a set of bookings. {@code balance()} is what is still owed:
 * total less what was paid at the counter ({@code prepaidAmount}) and receipts recorded since.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_invoices")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CustomerInvoice extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", nullable = false, length = 15)
    private BillingFrequency billingFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private InvoiceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    @Builder.Default
    private EmailStatus emailStatus = EmailStatus.NOT_SENT;

    @Column(name = "email_to", length = 255)
    private String emailTo;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "email_error", length = 255)
    private String emailError;

    @Column(name = "shipments_count", nullable = false)
    private int shipmentsCount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "total_weight", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal totalWeight = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "prepaid_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal prepaidAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "remarks", length = 255)
    private String remarks;

    public BigDecimal balance() {
        return totalAmount.subtract(prepaidAmount).subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    /** Approves a pending invoice. Idempotent for an already-approved one is the caller's check. */
    public void approve(UUID actor) {
        if (isCancelled()) {
            throw new BusinessRuleException("A cancelled invoice cannot be approved.");
        }
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.approvedAt = Instant.now();
        this.approvedBy = actor;
    }

    public void requireApproved() {
        if (!isApproved()) {
            throw new BusinessRuleException("Approve this invoice before recording payments against it.");
        }
    }

    public boolean isCancelled() {
        return status == InvoiceStatus.CANCELLED;
    }

    /** Adds a receipt and moves the status to PARTLY_PAID / PAID. */
    public void applyPayment(BigDecimal amount) {
        if (isCancelled()) {
            throw new BusinessRuleException("A cancelled invoice cannot take a payment.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero.");
        }
        if (amount.compareTo(balance()) > 0) {
            throw new BusinessRuleException("Payment %s is more than the outstanding balance %s."
                    .formatted(amount.toPlainString(), balance().toPlainString()));
        }
        this.amountPaid = amountPaid.add(amount);
        refreshStatus();
    }

    /** Undoes a receipt: its amount goes back on the balance and the status follows. */
    public void reversePayment(BigDecimal amount) {
        if (isCancelled()) {
            throw new BusinessRuleException("A cancelled invoice has no payments to reverse.");
        }
        this.amountPaid = amountPaid.subtract(amount).max(BigDecimal.ZERO);
        refreshStatus();
    }

    public void refreshStatus() {
        if (isCancelled()) {
            return;
        }
        this.status = balance().signum() == 0 ? InvoiceStatus.PAID
                : (amountPaid.signum() > 0 ? InvoiceStatus.PARTLY_PAID : InvoiceStatus.UNPAID);
    }

    public void cancel() {
        if (amountPaid.signum() > 0) {
            throw new BusinessRuleException("This invoice has payments recorded and cannot be cancelled.");
        }
        this.status = InvoiceStatus.CANCELLED;
    }
}
