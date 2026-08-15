package com.courier.modules.shipment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One branch's commission totals over a shipment search — the Commission Report's
 * branch-wise summary row. Grouped by {@code Shipment.bookingBranchId} (commission is
 * earned by the booking branch, not the delivery branch — see {@code ShipmentServiceImpl
 * .copyCharge}), same unpaged-then-reduce shape as {@link ShipmentSummaryStats}.
 */
public record BranchCommissionSummary(
        UUID bookingBranchId,
        long shipmentCount,
        BigDecimal totalNetAmount,
        BigDecimal commissionOnBasicFreight,
        BigDecimal branchCommissionOnOtherAmount,
        BigDecimal companyCommissionOnBasicFreight,
        BigDecimal totalCommission
) {
}
