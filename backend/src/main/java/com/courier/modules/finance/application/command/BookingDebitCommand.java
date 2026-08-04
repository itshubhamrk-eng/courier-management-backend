package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The booking branch's wallet debit for a PREPAID shipment — the mirror of
 * {@link DebitCommand} for the Shipment Booking seam, kept a distinct type rather than a
 * reused {@code DebitCommand} because the reason ({@code SBK}) and reference
 * ({@code SHIPMENT}) are fixed by the caller, not chosen by it.
 *
 * @param branchId       the booking branch
 * @param amount         strictly positive, the shipment's net amount
 * @param shipmentNumber the shipment this debit answers to ({@code referenceId})
 * @param remarks        shown on the statement
 */
public record BookingDebitCommand(
        UUID branchId,
        BigDecimal amount,
        String shipmentNumber,
        String remarks
) {
}
