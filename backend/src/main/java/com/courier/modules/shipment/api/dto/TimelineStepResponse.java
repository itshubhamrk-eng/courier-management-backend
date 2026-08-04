package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TimelineStepResponse",
        description = "One step of the Booked -> Manifest Created -> Out Scan -> Dispatched -> "
                + "Received -> Out For Delivery -> Delivered timeline")
public record TimelineStepResponse(
        ShipmentStatus status, String label, Instant changedAt, UUID changedBy, boolean completed
) {
}
