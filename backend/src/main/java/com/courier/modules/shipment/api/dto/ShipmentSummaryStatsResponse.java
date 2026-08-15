package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/** Aggregates for GET /api/v1/shipments/summary — same filters as the list endpoint. */
@Schema(name = "ShipmentSummaryStatsResponse", description = "Unpaged aggregates for a shipment search")
public record ShipmentSummaryStatsResponse(
        long totalCount,
        BigDecimal totalChargeableWeight,
        BigDecimal totalNetAmount,
        Map<ShipmentStatus, Long> statusCounts
) {
}
