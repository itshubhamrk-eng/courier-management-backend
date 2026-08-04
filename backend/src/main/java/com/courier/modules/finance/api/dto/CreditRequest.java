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
 * Body of {@code POST /api/v1/branch-wallet/credit}. {@code COMPANY_ADMIN} only.
 *
 * <p>The amount is always positive; the direction is the endpoint, not the sign. A single
 * signed-amount endpoint is how a missing minus turns a correction into a charge.
 *
 * <p>{@code subTransactionType} must be a reason that can appear on a credit — the service
 * refuses e.g. {@code SBK} (shipment booking) here with a 422 rather than filing a debit
 * reason against money going in.
 */
@Schema(name = "CreditRequest", description = "Manual credit to a branch wallet")
public record CreditRequest(

        @NotNull
        @Schema(description = "Whose wallet. Required for a company admin.") UUID branchId,

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        @DecimalMax(value = "10000000.0000", message = "must not exceed 10,000,000")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "2500.00") BigDecimal amount,

        @Schema(description = "Why. Defaults to MCR (manual credit). Must be creditable: "
                + "WRC, SRF, COD, COM, BST, MCR, TRI, ADJ.", example = "MCR")
        SubTransactionType subTransactionType,

        @Schema(description = "What referenceId points at. Defaults to MANUAL.", example = "MANUAL")
        ReferenceType referenceType,

        @Size(max = 100)
        @Schema(description = "The document this credit answers to, if any") String referenceId,

        @Size(max = 500)
        @Schema(description = "Shown on the statement", example = "Goodwill credit for delayed pickup")
        String remarks
) {
}
