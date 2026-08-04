package com.courier.modules.shipment.api.dto;

import com.courier.modules.manifest.domain.ManifestStatus;

import java.time.Instant;
import java.util.UUID;

public record DispatchManifestResponse(
        UUID manifestId, String manifestNumber, ManifestStatus status,
        UUID vehicleId, UUID driverUserId, Instant dispatchedAt, int shipmentCount
) {
}
