package com.courier.modules.shipment.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Unpaged aggregates over a shipment search — the Booking/Delivery Report summary row.
 * {@code totalNetAmount} sums only rows with a charge record, same "missing means no
 * charge row" rule {@code ShipmentSummaryResponse.netAmount} documents.
 */
public record ShipmentSummaryStats(
        long totalCount,
        BigDecimal totalChargeableWeight,
        BigDecimal totalNetAmount,
        Map<ShipmentStatus, Long> statusCounts
) {
}
