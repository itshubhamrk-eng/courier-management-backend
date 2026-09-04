package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of GET /api/v1/shipments/vendor-audit — the Vendor Audit Report's per-branch
 *  reconciliation table. See {@code VendorAuditRow} for field-by-field meaning and its
 *  one known limitation on the delivery-commission figures. */
@Schema(name = "VendorAuditRowResponse", description = "Per-branch vendor audit totals over a shipment search")
public record VendorAuditRowResponse(
        UUID branchId,
        long paidOrderCount,
        long paidOrderQuantity,
        BigDecimal paidOrderAmount,
        long topayOrderCount,
        long topayOrderQuantity,
        BigDecimal topayOrderAmount,
        BigDecimal paidCommission,
        BigDecimal deliveryCommission,
        long totalBookedOrderCount,
        long totalDeliveredOrderCount,
        BigDecimal bookingTotalCommission,
        BigDecimal deliveryTotalCommission,
        BigDecimal odaCharges,
        BigDecimal otherCharges,
        long cancelledOrderCount
) {
}
