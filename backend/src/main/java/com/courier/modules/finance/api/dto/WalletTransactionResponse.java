package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.PaymentStatus;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ledger entry.
 *
 * <p>Flat and fully denormalised — no nesting, no ids the client has to resolve. That is
 * what "export ready" means here: a page of these maps straight onto CSV columns, and the
 * labels are included so an export is readable without a lookup table.
 *
 * <p>{@code balanceBefore} / {@code balanceAfter} are the wallet's <em>available</em>
 * balance around this entry, as recorded at the time. They are facts, not recomputations.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "WalletTransactionResponse", description = "A single wallet ledger entry")
public record WalletTransactionResponse(
        UUID id,
        UUID companyId,
        UUID walletId,
        String transactionNo,

        @Schema(description = "CR (credit) or DR (debit)") TransactionType transactionType,
        @Schema(description = "Credit or Debit") String transactionTypeLabel,
        SubTransactionType subTransactionType,
        @Schema(description = "Readable reason, e.g. Wallet Recharge") String subTransactionTypeLabel,

        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,

        ReferenceType referenceType,
        String referenceId,
        String remarks,

        String paymentGateway,
        String paymentReference,
        PaymentStatus paymentStatus,

        UUID createdBy,
        String createdByName,
        Instant createdAt
) {
}
