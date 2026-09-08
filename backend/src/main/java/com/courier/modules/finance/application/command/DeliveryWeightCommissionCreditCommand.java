package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The delivery branch's wallet credit for weight-based delivery commission on a
 * delivered shipment ({@code amount = branch's deliveryCommissionRatePerKg *
 * max(shipment's chargeable weight, branch's deliveryCommissionMinWeightKg)}) — same
 * delivery-side shape as {@link DrsChargeCreditCommand}, a second, independent
 * commission that applies to every delivery.
 *
 * @param branchId       the delivery branch
 * @param amount         strictly positive
 * @param shipmentNumber the shipment this credit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record DeliveryWeightCommissionCreditCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
