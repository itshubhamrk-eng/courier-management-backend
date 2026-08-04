package com.courier.modules.shipment.api.dto;

import java.util.List;

public record BulkMovementResponse(List<MovementOutcomeResponse> results, long successCount, long failureCount) {
}
