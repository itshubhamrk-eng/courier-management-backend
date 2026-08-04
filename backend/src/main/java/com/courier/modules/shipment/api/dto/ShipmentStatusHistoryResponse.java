package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ShipmentStatusHistoryResponse", description = "One entry of a shipment's status timeline")
public record ShipmentStatusHistoryResponse(
        UUID id, ShipmentStatus status, ShipmentStatus previousStatus,
        String remarks, UUID branchId, UUID manifestId, UUID vehicleId,
        UUID changedBy, Instant changedAt
) {
}
