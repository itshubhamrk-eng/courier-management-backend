package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.WalletStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A branch wallet in full. Nulls are serialised, so a client can tell "absent" from
 * "not sent".
 *
 * <p>{@code totalBalance} is derived ({@code available + hold}) and returned rather than
 * left to the client: three clients computing the same sum is three chances to get it
 * wrong, and one of them will forget the hold.
 *
 * <p>There is no field here a client can write back. A wallet has no update endpoint — the
 * balance moves only through the transaction endpoints.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "WalletResponse", description = "Branch wallet in full")
public record WalletResponse(
        UUID id,
        UUID companyId,
        String walletNumber,
        UUID branchId,
        String branchCode,
        String branchName,
        WalletStatus status,

        @Schema(description = "Spendable now") BigDecimal availableBalance,
        @Schema(description = "Reserved against in-flight work; not spendable") BigDecimal holdBalance,
        @Schema(description = "available + hold") BigDecimal totalBalance,
        @Schema(example = "INR") String currency,

        UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt, Long version
) {
}
