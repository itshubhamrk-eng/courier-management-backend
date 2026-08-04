package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/branch-wallet/debit}. {@code COMPANY_ADMIN} only.
 *
 * <p>Positive amount, as with a credit. A wallet is prepaid, so a debit larger than the
 * available balance is refused (422) rather than allowed to overdraw — held money does not
 * count towards it.
 *
 * <p>{@code subTransactionType} must be a reason that can appear on a debit.
 */
@Schema(name = "DebitRequest", description = "Manual debit from a branch wallet")
public record DebitRequest(

        @NotNull
        @Schema(description = "Whose wallet. Required for a company admin.") UUID branchId,

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        @DecimalMax(value = "10000000.0000", message = "must not exceed 10,000,000")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "750.00") BigDecimal amount,

        @Schema(description = "Why. Defaults to MDB (manual debit). Must be debitable: "
                + "SBK, COD, COM, BST, MDB, TRO, ADJ, PNL.", example = "MDB")
        SubTransactionType subTransactionType,

        @Schema(description = "What referenceId points at. Defaults to MANUAL.", example = "MANUAL")
        ReferenceType referenceType,

        @Size(max = 100)
        @Schema(description = "The document this debit answers to, if any") String referenceId,

        @Size(max = 500)
        @Schema(description = "Shown on the statement", example = "Penalty for mis-declared weight")
        String remarks
) {
}
