package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of GET /api/v1/shipments/commission-summary — the Commission Report's
 *  branch-wise summary table. */
@Schema(name = "BranchCommissionSummaryResponse", description = "Commission totals for one booking branch over a shipment search")
public record BranchCommissionSummaryResponse(
        UUID bookingBranchId,
        long shipmentCount,
        BigDecimal totalNetAmount,
        BigDecimal commissionOnBasicFreight,
        BigDecimal branchCommissionOnOtherAmount,
        BigDecimal companyCommissionOnBasicFreight,
        BigDecimal totalCommission
) {
}
