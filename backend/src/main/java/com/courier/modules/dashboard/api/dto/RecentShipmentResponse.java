package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row in the dashboard's Recent Shipments card. */
public record RecentShipmentResponse(
        UUID id,
        String awb,
        String consignee,
        String destination,
        String status,
        Instant bookedAt,
        BigDecimal amount
) {
}
