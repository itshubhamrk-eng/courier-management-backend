package com.courier.modules.finance.application.command;

/**
 * Undo every settled wallet entry a shipment carries — the cancellation seam. Each entry's
 * own wallet is resolved from the ledger itself (not from a caller-supplied branch), since a
 * shipment's freight debit and its commission credit can sit on different branches' wallets.
 *
 * @param shipmentNumber the shipment being cancelled ({@code referenceId} to reverse)
 * @param remarks        shown on each reversal entry's statement line
 */
public record ShipmentReversalCommand(
        String shipmentNumber,
        String remarks
) {
}
