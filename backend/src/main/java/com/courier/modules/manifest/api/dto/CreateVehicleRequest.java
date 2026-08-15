package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.FuelType;
import com.courier.modules.manifest.domain.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "CreateVehicleRequest")
public record CreateVehicleRequest(
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
        UUID branchId,
        @Size(max = 500) String remarks
) {
}
