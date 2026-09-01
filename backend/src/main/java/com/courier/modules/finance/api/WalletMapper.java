package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CreditRequest;
import com.courier.modules.finance.api.dto.DebitRequest;
import com.courier.modules.finance.api.dto.RechargeOrderResponse;
import com.courier.modules.finance.api.dto.RechargeRequest;
import com.courier.modules.finance.api.dto.WalletResponse;
import com.courier.modules.finance.api.dto.WalletSummaryResponse;
import com.courier.modules.finance.api.dto.WalletTransactionResponse;
import com.courier.modules.finance.api.dto.WalletTransactionSearchRequest;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.application.command.CreditCommand;
import com.courier.modules.finance.application.command.DebitCommand;
import com.courier.modules.finance.application.command.RechargeCommand;
import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.finance.domain.WalletTransactionCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Wire contract ↔ application/domain types for branch wallets. */
@Component
@RequiredArgsConstructor
public class WalletMapper {

    private final BranchDirectoryPort branchDirectory;

    // ------------------------------------------------------------------ inbound

    public RechargeCommand toCommand(RechargeRequest r) {
        return new RechargeCommand(r.branchId(), r.amount(), r.gatewayOrderId(),
                r.paymentReference(), r.signature(), r.remarks());
    }

    public CreditCommand toCommand(CreditRequest r) {
        return new CreditCommand(r.branchId(), r.amount(), r.subTransactionType(),
                r.referenceType(), r.referenceId(), r.remarks());
    }

    public DebitCommand toCommand(DebitRequest r) {
        return new DebitCommand(r.branchId(), r.amount(), r.subTransactionType(),
                r.referenceType(), r.referenceId(), r.remarks());
    }

    /**
     * Dates in, instants out. {@code toDate} becomes the start of the <em>next</em> day so
     * the specification's exclusive upper bound still includes the whole day the caller
     * asked for — the alternative silently drops the last day of every statement.
     */
    public WalletTransactionCriteria toCriteria(WalletTransactionSearchRequest r) {
        WalletTransactionSearchRequest safe =
                r == null ? WalletTransactionSearchRequest.empty() : r;
        return new WalletTransactionCriteria(
                null, null,
                safe.transactionType(), safe.subTransactionType(),
                safe.referenceType(), safe.paymentStatus(),
                safe.referenceId(), safe.transactionNo(), safe.paymentReference(),
                startOfDay(safe.fromDate()), startOfNextDay(safe.toDate()),
                safe.minAmount(), safe.maxAmount(), safe.search());
    }

    // ----------------------------------------------------------------- outbound

    public WalletResponse toResponse(Wallet w) {
        BranchDirectoryPort.BranchRef branch =
                branchDirectory.findBranch(w.getBranchId(), w.getCompanyId()).orElse(null);
        return new WalletResponse(
                w.getId(), w.getCompanyId(), w.getWalletNumber(), w.getBranchId(),
                branch == null ? null : branch.branchCode(),
                branch == null ? null : branch.branchName(),
                w.getStatus(),
                w.getAvailableBalance(), w.getHoldBalance(), w.getTotalBalance(), w.getCurrency(),
                w.getCreatedBy(), w.getCreatedAt(), w.getUpdatedBy(), w.getUpdatedAt(), w.getVersion());
    }

    public WalletSummaryResponse toResponse(WalletService.WalletSummary s) {
        Wallet w = s.wallet();
        return new WalletSummaryResponse(
                w.getId(), w.getWalletNumber(), w.getBranchId(), s.branchCode(), s.branchName(),
                w.getStatus(), w.getCurrency(),
                w.getAvailableBalance(), w.getHoldBalance(), w.getTotalBalance(),
                s.todayCredit(), s.todayDebit(), s.monthCredit(), s.monthDebit(),
                s.totalCredit(), s.totalDebit(),
                s.transactionCount(), s.lastTransactionAt(),
                s.lastRechargeAmount(), s.lastRechargeAt());
    }

    public WalletTransactionResponse toResponse(WalletTransaction t) {
        String createdByName = branchDirectory.findUser(t.getCreatedBy(), t.getCompanyId())
                .map(BranchDirectoryPort.UserRef::displayName).orElse(null);
        return new WalletTransactionResponse(
                t.getId(), t.getCompanyId(), t.getWalletId(), t.getTransactionNo(),
                t.getTransactionType(),
                t.getTransactionType() == null ? null : t.getTransactionType().getLabel(),
                t.getSubTransactionType(),
                t.getSubTransactionType() == null ? null : t.getSubTransactionType().getLabel(),
                t.getAmount(), t.getBalanceBefore(), t.getBalanceAfter(),
                t.getReferenceType(), t.getReferenceId(), t.getRemarks(),
                t.getPaymentGateway(), t.getPaymentReference(), t.getPaymentStatus(),
                t.getCreatedBy(), createdByName, t.getCreatedAt());
    }

    public RechargeOrderResponse toResponse(PaymentGatewayPort.GatewayOrder order,
                                            UUID walletId, String walletNumber, UUID branchId) {
        return new RechargeOrderResponse(walletId, walletNumber, branchId,
                order.gateway(), order.orderId(), order.amountMinor(), order.currency(),
                order.publicKey(), order.receipt());
    }

    // ------------------------------------------------------------------ helpers

    private static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
