package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "VehicleResponse")
public record VehicleResponse(
        UUID id, String vehicleNumber, UUID vehicleTypeId, BigDecimal capacityKg,
        VehicleStatus status, String remarks, Long version
) {
}
