package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of GET /api/v1/shipments/commission-ledger — the Commission Credit report. */
@Schema(name = "CommissionLedgerRowResponse", description = "A shipment's booking-branch commission, credited or pending")
public record CommissionLedgerRowResponse(
        UUID shipmentId,
        String shipmentNumber,
        UUID bookingBranchId,
        ShipmentStatus status,
        String paymentModeName,
        BigDecimal commission,
        boolean credited,
        Instant creditedAt,
        String awaiting
) {
}
