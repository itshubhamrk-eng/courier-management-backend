package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The delivery branch's wallet debit for a shipment collected at delivery ({@code TO_PAY}
 * or {@code COD}) — the delivery-side mirror of {@link BookingDebitCommand}. The branch
 * physically holds the cash it just collected from the consignee; this debit is the ledger
 * entry recording that the collected amount is owed to the company, the same relationship
 * {@link BookingDebitCommand} records at booking time for a PREPAID shipment, just deferred
 * to the branch and moment the money actually changes hands.
 *
 * @param branchId       the delivery branch
 * @param amount         strictly positive, the shipment's net amount
 * @param shipmentNumber the shipment this debit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record CodDeliveryDebitCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
