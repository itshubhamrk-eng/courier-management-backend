package com.courier.modules.shipment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One shipment's booking-branch commission, credited or still pending — a row of the Commission
 * Credit report. {@code credited} is true once the commission was posted to the booking branch's
 * wallet (reference = the shipment number); otherwise {@code awaiting} says which scan triggers it.
 */
public record CommissionLedgerRow(
        UUID shipmentId,
        String shipmentNumber,
        LocalDate bookingDate,
        UUID bookingBranchId,
        ShipmentStatus status,
        String paymentModeName,
        BigDecimal commission,
        boolean credited,
        Instant creditedAt,
        String awaiting
) {
}
