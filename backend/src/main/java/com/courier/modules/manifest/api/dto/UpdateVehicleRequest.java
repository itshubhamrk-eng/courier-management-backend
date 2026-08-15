package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.FuelType;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.modules.manifest.domain.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/vehicles/{id}}. Full replacement of the editable fields.
 * {@code version} required. Active/inactive has its own endpoints.
 */
@Schema(name = "UpdateVehicleRequest")
public record UpdateVehicleRequest(
        @NotBlank @Size(max = 30) String vehicleNumber,
        @NotNull VehicleType vehicleType,
        @Size(max = 50) String make,
        @Size(max = 50) String model,
        FuelType fuelType,
        @DecimalMin(value = "0", inclusive = true) BigDecimal capacityKg,
        @DecimalMin(value = "0", inclusive = true) BigDecimal currentOdometer,
        LocalDate purchaseDate,
        LocalDate registrationDate,
        LocalDate insuranceExpiry,
        LocalDate pucExpiry,
        LocalDate fitnessExpiry,
        LocalDate permitExpiry,
        @NotNull VehicleStatus status,
        UUID branchId,
        @Size(max = 500) String remarks,
        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
