package com.courier.modules.shipment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One branch's shipment volume/outcome totals over a shipment search — the Branch
 * Performance Report's per-branch row. Grouped by {@code Shipment.bookingBranchId}, same
 * unpaged-then-reduce shape as {@link BranchCommissionSummary}.
 *
 * @param inTransitCount shipmentCount minus delivered/returned/cancelled — everything
 *                        still moving through the pipeline
 */
public record BranchPerformanceSummary(
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
