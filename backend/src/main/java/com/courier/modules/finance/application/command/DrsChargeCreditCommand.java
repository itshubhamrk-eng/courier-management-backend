package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The delivery branch's wallet credit for DRS commission on a delivered shipment
 * ({@code drsCharge = branch's drsChargePerQty * item quantity}) — the same
 * delivery-side shape as {@link CodDeliveryDebitCommand}, just for a commission that
 * applies to every delivery, not only collect-at-delivery ones.
 *
 * @param branchId       the delivery branch
 * @param amount         strictly positive, {@code drsChargePerQty * qty}
 * @param shipmentNumber the shipment this credit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record DrsChargeCreditCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
