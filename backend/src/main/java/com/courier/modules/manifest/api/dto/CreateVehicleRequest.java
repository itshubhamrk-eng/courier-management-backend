package com.courier.modules.manifest.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "CreateVehicleRequest")
public record CreateVehicleRequest(
        @NotBlank @Size(max = 30) String vehicleNumber,
        UUID vehicleTypeId,
        @DecimalMin(value = "0", inclusive = true) BigDecimal capacityKg,
        @Size(max = 500) String remarks
) {
}
