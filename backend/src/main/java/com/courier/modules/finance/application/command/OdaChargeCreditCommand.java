package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Credit of the delivery branch's own ODA charge amount for one delivered shipment that carries
 * an ODA charge ({@code ODA}, referencing the shipment number).
 *
 * @param branchId       the delivery branch
 * @param amount         strictly positive — the branch's {@code odaChargeAmount}
 * @param shipmentNumber the shipment this credit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record OdaChargeCreditCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
