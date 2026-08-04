package com.courier.modules.shipment.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published {@code AFTER_COMMIT} only — a shipment's own transaction has already
 * succeeded by the time any of these fire, the same discipline
 * {@code finance.application.event.WalletEvent} follows for money movements.
 */
public sealed interface ShipmentEvent {

    UUID companyId();

    Instant occurredAt();

    /**
     * A PREPAID shipment was booked and its booking branch's wallet still needs debiting.
     * Published only when the payment mode collects at booking — see
     * {@code ShipmentServiceImpl.create}. Handled by
     * {@code ShipmentBookingWalletListener}, which calls {@code WalletService
     * .debitForBooking} — the seam {@code MEMORY/modules/branch-wallet.md} documented as
     * deliberately not built ahead of this, its consumer.
     */
    record PrepaidBookingConfirmed(
            UUID shipmentId,
            UUID companyId,
            UUID bookingBranchId,
            String shipmentNumber,
            BigDecimal netAmount,
            Instant occurredAt
    ) implements ShipmentEvent {
    }
}
