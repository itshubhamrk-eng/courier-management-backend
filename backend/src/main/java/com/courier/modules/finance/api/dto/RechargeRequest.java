package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of the two recharge calls.
 *
 * <p><b>{@code POST /branch-wallet/recharge/order}</b> uses {@code branchId} and
 * {@code amount}: the server opens an order at the gateway for exactly that amount and
 * returns it for the browser checkout. Nothing is credited.
 *
 * <p><b>{@code POST /branch-wallet/recharge}</b> uses the three gateway fields that
 * checkout hands back. The server verifies the signature, then asks the gateway what was
 * actually paid and credits <em>that</em>. <b>{@code amount} is ignored at this step</b> —
 * a figure supplied by the same client that supplies the payment proves nothing, and
 * trusting it is how a ₹1 payment becomes a ₹10,000 credit.
 *
 * <p>{@code branchId} is optional for a branch user (their own branch is the only possible
 * answer) and required for a company admin, who has no branch of their own.
 */
@Schema(name = "RechargeRequest",
        description = "Open a gateway order, or settle the payment it produced")
public record RechargeRequest(

        @Schema(description = "Whose wallet. Omit to use your own branch.") UUID branchId,

        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        @DecimalMax(value = "10000000.0000", message = "must not exceed 10,000,000")
        @Digits(integer = 15, fraction = 4)
        @Schema(description = "Amount to recharge. Used when opening the order; ignored when "
                + "settling, where the gateway is the source of truth.", example = "5000.00")
        BigDecimal amount,

        @Size(max = 100)
        @Schema(description = "Gateway order id returned by the order step", example = "order_Qk1a2B3c4D5e6F")
        String gatewayOrderId,

        @Size(max = 100)
        @Schema(description = "Gateway payment id. Also the idempotency key — settling the "
                + "same payment twice credits the wallet once.", example = "pay_Qk1a2B3c4D5e6F")
        String paymentReference,

        @Size(max = 512)
        @Schema(description = "Gateway signature over the order and payment ids")
        String signature,

        @Size(max = 500) String remarks
) {
}
