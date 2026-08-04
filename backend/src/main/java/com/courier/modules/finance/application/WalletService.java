package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.BookingDebitCommand;
import com.courier.modules.finance.application.command.CreditCommand;
import com.courier.modules.finance.application.command.DebitCommand;
import com.courier.modules.finance.application.command.RechargeCommand;
import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.finance.domain.WalletTransactionCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Branch wallet use cases.
 *
 * <p>Every method that moves money takes the branch, not the wallet: a branch has exactly
 * one wallet, so an API that took a wallet id would only invite a caller to guess one.
 * A null {@code branchId} means "the caller's own branch", which is the only thing a branch
 * user can mean; a company admin must name one.
 *
 * <p><b>There is no method that sets a balance.</b> That is the module's central rule and
 * it is expressed by this interface's shape, not only by documentation.
 */
public interface WalletService {

    /**
     * Creates the wallet for a branch if it does not have one yet, and returns it either
     * way. Idempotent.
     *
     * <p>Called from two places: the {@code BranchCreated} listener, so a new branch has a
     * wallet immediately, and every read path, so branches that predate this module acquire
     * one on first access instead of 404ing forever.
     */
    Wallet getOrCreateForBranch(UUID branchId);

    /** The wallet the caller may see. Read-only; does not create. */
    Wallet getForBranch(UUID branchId);

    /** Balances plus the derived figures a dashboard shows. */
    WalletSummary summarise(UUID branchId);

    /** The statement, paged and filtered. Criteria are pinned to the resolved wallet. */
    Page<WalletTransaction> searchTransactions(UUID branchId, WalletTransactionCriteria criteria,
                                               Pageable pageable);

    /**
     * Step one of a recharge: fixes the amount at the gateway and returns an order for the
     * browser. Credits nothing.
     */
    PaymentGatewayPort.GatewayOrder openRecharge(RechargeCommand command);

    /**
     * Step two: verifies the gateway's confirmation, asks the gateway what was actually
     * paid, and credits that. Idempotent on the gateway payment id — a client that retries,
     * or a webhook that arrives twice, credits the wallet once.
     */
    WalletTransaction completeRecharge(RechargeCommand command);

    /** Manual credit by a company admin. */
    WalletTransaction credit(CreditCommand command);

    /** Manual debit by a company admin. Refused if the balance is insufficient. */
    WalletTransaction debit(DebitCommand command);

    /**
     * Debits the booking branch's wallet for a PREPAID shipment ({@code SBK}, referencing
     * the shipment number). Unlike {@link #debit}, this is <b>not</b> restricted to
     * {@code COMPANY_ADMIN} — Shipment Booking has already decided who may book through
     * that branch, and this seam only moves the money that decision earns; it still
     * refuses an out-of-scope branch and an insufficient balance exactly as {@link #debit}
     * does. Deliberately not built ahead of its consumer — see
     * {@code MEMORY/modules/branch-wallet.md}'s "Booking debit seam".
     *
     * @param command branch, amount, and the shipment the debit answers to
     * @throws com.courier.shared.exception.BusinessRuleException insufficient balance, a
     *         non-ACTIVE wallet, or a non-positive amount
     * @throws com.courier.shared.exception.ForbiddenException the branch is not the
     *         caller's own and the caller is not a company admin
     */
    WalletTransaction debitForBooking(BookingDebitCommand command);

    /**
     * Derived view of a wallet, assembled for the summary endpoint.
     *
     * @param branchCode      / {@code branchName} — the labels a statement needs, resolved
     *                        through the branch directory
     * @param todayCredit     settled credits since midnight UTC
     * @param todayDebit      settled debits since midnight UTC
     * @param monthCredit     settled credits this calendar month (UTC)
     * @param monthDebit      settled debits this calendar month (UTC)
     * @param totalCredit     settled credits over the wallet's life
     * @param totalDebit      settled debits over the wallet's life
     * @param transactionCount entries in the ledger
     * @param lastTransactionAt when the ledger last moved, or null for an untouched wallet
     * @param lastRechargeAmount / {@code lastRechargeAt} — the most recent settled recharge
     */
    record WalletSummary(
            Wallet wallet,
            String branchCode,
            String branchName,
            BigDecimal todayCredit,
            BigDecimal todayDebit,
            BigDecimal monthCredit,
            BigDecimal monthDebit,
            BigDecimal totalCredit,
            BigDecimal totalDebit,
            long transactionCount,
            Instant lastTransactionAt,
            BigDecimal lastRechargeAmount,
            Instant lastRechargeAt
    ) {
    }
}
