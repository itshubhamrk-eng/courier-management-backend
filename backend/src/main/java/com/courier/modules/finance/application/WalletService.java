package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.BookingDebitCommand;
import com.courier.modules.finance.application.command.CodDeliveryDebitCommand;
import com.courier.modules.finance.application.command.CommissionCreditCommand;
import com.courier.modules.finance.application.command.CreditCommand;
import com.courier.modules.finance.application.command.DebitCommand;
import com.courier.modules.finance.application.command.DrsChargeCreditCommand;
import com.courier.modules.finance.application.command.RechargeCommand;
import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.finance.domain.WalletTransactionCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    /** One summary per branch of the caller's company — the Finance Report's company-wide
     *  table. `COMPANY_ADMIN`/`FINANCE_USER` only, since every other wallet read is scoped
     *  to a single branch. */
    List<WalletSummary> companySummary();

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

    /**
     * Same settlement as {@link #completeRecharge}, reached from a Razorpay webhook instead
     * of the browser — covers a tab closed after the gateway captured the payment but before
     * the browser confirmed it. No {@code @PreAuthorize}: a webhook carries no authenticated
     * user, so the caller ({@code RazorpayWebhookController}) must already have verified the
     * webhook signature and resolved {@code companyId}/{@code branchId} from the order's own
     * notes before calling this — never from anything the webhook body claims about itself.
     * Idempotent on the gateway payment id, same as {@link #completeRecharge}.
     */
    WalletTransaction settleFromWebhook(UUID companyId, UUID branchId, String gatewayOrderId,
                                        String paymentId);

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
     * Debits the delivery branch's wallet for a shipment collected at delivery
     * ({@code COD}, referencing the shipment number) — the delivery-side mirror of
     * {@link #debitForBooking}. Same non-{@code COMPANY_ADMIN}-only shape: Shipment
     * Movement has already decided who may deliver through that branch, and this seam
     * only moves the money that decision earns.
     *
     * @param command branch, amount, and the shipment the debit answers to
     * @throws com.courier.shared.exception.BusinessRuleException insufficient balance, a
     *         non-ACTIVE wallet, or a non-positive amount
     * @throws com.courier.shared.exception.ForbiddenException the branch is not the
     *         caller's own and the caller is not a company admin
     */
    WalletTransaction debitForCodDelivery(CodDeliveryDebitCommand command);

    /**
     * Credits the delivery branch's wallet with DRS commission on a delivered shipment
     * ({@code DRS}, referencing the shipment number) — {@code drsCharge = branch's own
     * drsChargePerQty * item quantity}, published from {@code ShipmentServiceImpl.deliver}
     * for every delivery, not only collect-at-delivery ones. Same non-{@code
     * COMPANY_ADMIN}-only shape as {@link #debitForCodDelivery}.
     *
     * @param command branch, amount, and the shipment the credit answers to
     * @throws com.courier.shared.exception.BusinessRuleException a non-ACTIVE wallet or a
     *         non-positive amount
     * @throws com.courier.shared.exception.ForbiddenException the branch is not the
     *         caller's own and the caller is not a company admin
     */
    WalletTransaction creditForDrsCharge(DrsChargeCreditCommand command);

    /**
     * Credits the booking branch's wallet with its commission share of a PREPAID shipment
     * ({@code COM}, referencing the shipment number) — posted right after
     * {@link #debitForBooking} settles, only when the branch has {@code instantCommission}
     * on. Same non-{@code COMPANY_ADMIN}-only shape as {@link #debitForBooking}.
     *
     * @param command branch, amount, and the shipment the credit answers to
     * @throws com.courier.shared.exception.BusinessRuleException a non-ACTIVE wallet or a
     *         non-positive amount
     * @throws com.courier.shared.exception.ForbiddenException the branch is not the
     *         caller's own and the caller is not a company admin
     */
    WalletTransaction creditCommission(CommissionCreditCommand command);

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
