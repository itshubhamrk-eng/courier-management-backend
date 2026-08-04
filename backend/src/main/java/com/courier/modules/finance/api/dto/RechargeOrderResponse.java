package com.courier.modules.finance.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Everything the browser checkout needs to open a payment — and nothing more.
 *
 * <p>{@code publicKey} is the gateway's publishable key. The signing secret never leaves
 * the server: it is what proves the confirmation genuine when the payment comes back, and a
 * secret the browser has seen proves nothing.
 *
 * <p>{@code amountMinor} is in the smallest currency unit (paise), because that is the
 * gateway's contract. It is stated in the field name so nobody has to guess which side of
 * the boundary this number is on.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RechargeOrderResponse", description = "A gateway order, ready for checkout")
public record RechargeOrderResponse(
        UUID walletId,
        String walletNumber,
        UUID branchId,
        @Schema(example = "RAZORPAY") String gateway,
        String orderId,
        @Schema(description = "Amount in the smallest currency unit (paise)", example = "500000")
        long amountMinor,
        @Schema(example = "INR") String currency,
        @Schema(description = "Publishable gateway key. Never the secret.") String publicKey,
        @Schema(description = "Our own reference, echoed back by the gateway") String receipt
) {
}
