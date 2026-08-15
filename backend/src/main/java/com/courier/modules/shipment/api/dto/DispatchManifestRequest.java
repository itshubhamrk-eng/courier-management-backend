package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record DispatchManifestRequest(
        @NotNull UUID manifestId,
        @NotNull UUID vehicleId,
        @NotNull UUID driverUserId,
        Instant departureTime
) {
}
