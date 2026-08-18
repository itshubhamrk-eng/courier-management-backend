package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of GET /api/v1/shipments/branch-performance — the Branch Performance Report's
 *  per-branch summary table. */
@Schema(name = "BranchPerformanceSummaryResponse", description = "Shipment volume/outcome totals for one booking branch over a shipment search")
public record BranchPerformanceSummaryResponse(
        UUID bookingBranchId,
        long shipmentCount,
        long deliveredCount,
        long inTransitCount,
        long returnedCount,
        long cancelledCount,
        BigDecimal totalChargeableWeight,
        BigDecimal totalNetAmount
) {
}
