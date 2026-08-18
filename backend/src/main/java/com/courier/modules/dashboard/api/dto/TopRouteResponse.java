package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the Top Routes card: a delivery branch's month-to-date shipment count/revenue. */
public record TopRouteResponse(UUID branchId, String branchCode, String branchName,
                                long shipmentCount, BigDecimal revenue) {
}
