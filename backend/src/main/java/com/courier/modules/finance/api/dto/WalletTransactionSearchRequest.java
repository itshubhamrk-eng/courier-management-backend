package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.PaymentStatus;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/branch-wallet/transactions}, bound as a parameter
 * object.
 *
 * <p>{@code branchId} selects <em>whose</em> statement and is authorised separately: a
 * branch user may only ask for their own, and asking for another's returns 404. There is
 * deliberately no {@code walletId} filter — the wallet is derived from the branch, so a
 * caller cannot aim the query at one by guessing an id.
 *
 * <p>{@code fromDate} is inclusive and {@code toDate} is inclusive of the whole day: a
 * statement "to the 28th" that stopped at 00:00 on the 28th would silently drop a day, which
 * is the kind of off-by-one that only shows up during a reconciliation dispute.
 */
@Schema(name = "WalletTransactionSearchRequest", description = "Wallet ledger filters")
public record WalletTransactionSearchRequest(

        @Schema(description = "Whose statement. Omit to use your own branch.") UUID branchId,

        @Schema(description = "CR and/or DR") Set<TransactionType> transactionType,
        @Schema(description = "Match any of these reasons") Set<SubTransactionType> subTransactionType,
        Set<ReferenceType> referenceType,
        Set<PaymentStatus> paymentStatus,

        @Size(max = 100) String referenceId,
        @Size(max = 40) String transactionNo,
        @Size(max = 100) String paymentReference,

        @Schema(description = "Inclusive, UTC", example = "2026-07-01") LocalDate fromDate,
        @Schema(description = "Inclusive of the whole day, UTC", example = "2026-07-28") LocalDate toDate,

        @DecimalMin("0.0") BigDecimal minAmount,
        @DecimalMin("0.0") BigDecimal maxAmount,

        @Size(max = 100)
        @Schema(description = "Free text over entry number, remarks, reference id and payment id")
        String search
) {

        public static WalletTransactionSearchRequest empty() {
                return new WalletTransactionSearchRequest(null, null, null, null, null,
                        null, null, null, null, null, null, null, null);
        }
}
