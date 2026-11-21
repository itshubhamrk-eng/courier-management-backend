package com.courier.modules.shipment.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DrsSummaryResponse(
        UUID deliveryUserId, String deliveryPersonName, String deliveryPersonMobile, UUID deliveryBranchId, LocalDate runDate,
        String drsNumber, int shipmentCount, int deliveredCount, int pendingCount, int failedCount
) {
}
