package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.ManifestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ManifestResponse")
public record ManifestResponse(
        UUID id, String manifestNumber, UUID bookingBranchId, UUID deliveryBranchId,
        UUID vehicleId, UUID driverUserId, ManifestStatus status,
        Instant dispatchedAt, Instant departureTime, Instant completedAt, String remarks,
        Instant createdAt, Instant updatedAt, Long version,
        int shipmentCount, BigDecimal totalWeight, int totalPackages
) {
}
