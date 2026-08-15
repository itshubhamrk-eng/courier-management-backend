package com.courier.modules.distance.api.dto;

import com.courier.modules.distance.domain.AddressType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AddressDistanceResponse", description = "Resolved road distance between two addresses")
public record AddressDistanceResponse(
        UUID id, UUID companyId, AddressType addressType, UUID fromId, UUID toId,
        BigDecimal distanceKm, BigDecimal distanceMeter, BigDecimal requiredTimeMinutes,
        Instant createdAt
) {
}
