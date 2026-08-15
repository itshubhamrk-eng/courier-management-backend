package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The booking branch's wallet credit for its commission share of a PREPAID shipment
 * ({@code COM}, referencing the shipment number) — posted alongside
 * {@link BookingDebitCommand} only when the branch has {@code instantCommission} on.
 *
 * @param branchId       the booking branch
 * @param amount         strictly positive, the shipment charge's stored branch commission
 * @param shipmentNumber the shipment this credit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record CommissionCreditCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
