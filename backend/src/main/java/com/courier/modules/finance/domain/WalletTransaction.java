package com.courier.modules.finance.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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
import java.util.UUID;

/**
 * One entry in a branch wallet's ledger. Append-only.
 *
 * <p>Every balance change on a {@link Wallet} writes exactly one of these, in the same
 * transaction. Nothing else may move the balance, which is what lets a statement be
 * reconstructed and reconciled.
 *
 * <p>{@code balanceBefore} / {@code balanceAfter} are denormalised on purpose. Replaying
 * a ledger to find the balance at a point in time is fine on a whiteboard and unusable in
 * production: it makes every statement O(history) and it silently rewrites the past when
 * a correcting entry is inserted out of order. Storing the running balance means an entry
 * is a fact about the moment it happened.
 *
 * <p>The only mutable field is {@link #paymentStatus} — a gateway payment starts
 * {@code PENDING} (intent recorded, no money moved) and is later settled or failed. The
 * amounts and balances are {@code updatable = false}: an entry is never edited, a
 * correction is a new entry.
 */
@Entity(name = "WalletTransaction")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "wallet_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wallet_txn_no", columnNames = {"transaction_no"}),
                // Global, not per company: one merchant account serves every company,
                // so a gateway payment id must credit exactly one wallet platform-wide.
                @UniqueConstraint(name = "uk_wallet_txn_payment_ref",
                        columnNames = {"payment_reference"})
        },
        indexes = {
                @Index(name = "idx_wallet_txn_wallet_created", columnList = "wallet_id, created_at"),
                @Index(name = "idx_wallet_txn_company_created", columnList = "company_id, created_at"),
                @Index(name = "idx_wallet_txn_type",
                        columnList = "company_id, transaction_type, sub_transaction_type"),
                @Index(name = "idx_wallet_txn_reference",
                        columnList = "company_id, reference_type, reference_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class WalletTransaction extends CompanyOwnedEntity {

    /** Human-facing entry number. Immutable, globally unique, quoted on receipts. */
    @Column(name = "transaction_no", nullable = false, updatable = false, length = 40)
    private String transactionNo;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 2)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sub_transaction_type", nullable = false, updatable = false, length = 10)
    private SubTransactionType subTransactionType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19,
            scale = Wallet.MONEY_SCALE)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, updatable = false, precision = 19,
            scale = Wallet.MONEY_SCALE)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19,
            scale = Wallet.MONEY_SCALE)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", updatable = false, length = 20)
    private ReferenceType referenceType;

    /**
     * Free-form id of whatever this entry refers to — a shipment AWB, a settlement batch,
     * a gateway order. A string rather than a UUID because not every reference is one.
     */
    @Column(name = "reference_id", updatable = false, length = 100)
    private String referenceId;

    @Column(name = "remarks", updatable = false, length = 500)
    private String remarks;

    @Column(name = "payment_gateway", updatable = false, length = 30)
    private String paymentGateway;

    /** The gateway's own payment id. Unique per company — this is the idempotency key. */
    @Column(name = "payment_reference", updatable = false, length = 100)
    private String paymentReference;

    /**
     * The one mutable field: a payment is opened PENDING and settled later. Everything
     * else about an entry is fixed at the moment it is written.
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    // ---------------------------------------------------------------- behaviour

    public boolean isCredit() {
        return transactionType != null && transactionType.isCredit();
    }

    public boolean isDebit() {
        return transactionType != null && transactionType.isDebit();
    }

    /** True once the money has actually moved: either a non-payment entry, or a settled one. */
    public boolean isSettled() {
        return paymentStatus == null || paymentStatus == PaymentStatus.SUCCESS;
    }

    /** Signed amount, for summing a ledger: credits positive, debits negative. */
    public BigDecimal getSignedAmount() {
        BigDecimal value = Wallet.normalise(amount);
        return isDebit() ? value.negate() : value;
    }
}
