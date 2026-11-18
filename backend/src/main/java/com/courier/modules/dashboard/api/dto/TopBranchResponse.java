package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the Top Branches card: a booking branch's month-to-date shipment count, actual weight and revenue. */
public record TopBranchResponse(UUID branchId, String branchCode, String branchName,
                                long shipmentCount, BigDecimal actualWeight, BigDecimal revenue) {
}
