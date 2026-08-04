package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A wallet recharge, in either of its two steps.
 *
 * <p>One command for both because the client sends one body to one endpoint shape; which
 * step it is depends on whether the gateway fields are present.
 *
 * <ol>
 *   <li><b>Open</b> — {@code branchId} and {@code amount}. The service fixes the amount at
 *       the gateway and returns an order for the browser. Nothing is credited.</li>
 *   <li><b>Settle</b> — the three gateway fields, handed back by checkout. The service
 *       verifies the signature, asks the gateway what was actually paid, and credits
 *       <em>that</em> figure. {@code amount} is ignored at this step: a number the client
 *       supplies alongside a payment it also supplies proves nothing.</li>
 * </ol>
 *
 * @param branchId         whose wallet; null means "the caller's own branch"
 * @param amount           what to open the gateway order for. Step 1 only
 * @param gatewayOrderId   the order the payment settled. Step 2
 * @param paymentReference the gateway's payment id — also the idempotency key. Step 2
 * @param signature        the gateway's HMAC over order and payment id. Step 2
 * @param remarks          free text, shown on the statement
 */
public record RechargeCommand(
        UUID branchId,
        BigDecimal amount,
        String gatewayOrderId,
        String paymentReference,
        String signature,
        String remarks
) {

    /** True when this carries a gateway confirmation to settle rather than an amount to open. */
    public boolean isSettlement() {
        return notBlank(gatewayOrderId) || notBlank(paymentReference) || notBlank(signature);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
