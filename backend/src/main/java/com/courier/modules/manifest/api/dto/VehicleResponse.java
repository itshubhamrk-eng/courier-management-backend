package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.FuelType;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.modules.manifest.domain.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "VehicleResponse")
public record VehicleResponse(
        UUID id,
        UUID companyId,
        String vehicleNumber,
        VehicleType vehicleType,
        String make,
        String model,
        FuelType fuelType,
        BigDecimal capacityKg,
        BigDecimal currentOdometer,
        LocalDate purchaseDate,
        LocalDate registrationDate,
        LocalDate insuranceExpiry,
        LocalDate pucExpiry,
        LocalDate fitnessExpiry,
        LocalDate permitExpiry,
        VehicleStatus status,
        UUID branchId,
        String remarks,
        boolean active,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Long version
) {
}
