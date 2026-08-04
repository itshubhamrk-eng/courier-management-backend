package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of {@code POST /api/v1/branch-wallet/topup-requests}. */
@Schema(name = "CreateTopupRequestRequest", description = "Ask the company admin to fund a branch wallet")
public record CreateTopupRequestRequest(

        @Schema(description = "Whose wallet. A branch caller's own branch is used regardless "
                + "of what is passed here; required for a company admin.")
        UUID branchId,

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        @DecimalMax(value = "10000000.0000", message = "must not exceed 10,000,000")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "5000.00") BigDecimal amount,

        @Size(max = 500)
        @Schema(description = "Why the branch needs it", example = "Cash running low for COD payouts")
        String remarks
) {
}
