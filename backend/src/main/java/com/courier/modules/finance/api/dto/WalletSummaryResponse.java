package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.WalletStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The wallet dashboard in one response.
 *
 * <p>Every derived figure is computed server-side from the ledger. The alternative — hand
 * the client a page of transactions and let it add them up — gives a different answer per
 * client, and a wrong one as soon as the page boundary moves.
 *
 * <p>All the period figures count <b>settled</b> entries only: a recharge whose payment has
 * not been confirmed is not money, and showing it as today's credit would tell a branch it
 * can spend what it does not have.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "WalletSummaryResponse", description = "Branch wallet dashboard figures")
public record WalletSummaryResponse(
        UUID walletId,
        String walletNumber,
        UUID branchId,
        String branchCode,
        String branchName,
        WalletStatus status,
        String currency,

        BigDecimal availableBalance,
        BigDecimal holdBalance,
        BigDecimal totalBalance,

        @Schema(description = "Settled credits since 00:00 UTC") BigDecimal todayCredit,
        @Schema(description = "Settled debits since 00:00 UTC") BigDecimal todayDebit,
        @Schema(description = "Settled credits this calendar month, UTC") BigDecimal monthCredit,
        @Schema(description = "Settled debits this calendar month, UTC") BigDecimal monthDebit,
        @Schema(description = "Settled credits over the wallet's life") BigDecimal totalCredit,
        @Schema(description = "Settled debits over the wallet's life") BigDecimal totalDebit,

        long transactionCount,
        Instant lastTransactionAt,
        BigDecimal lastRechargeAmount,
        Instant lastRechargeAt
) {
}
