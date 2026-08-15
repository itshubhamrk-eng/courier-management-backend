package com.courier.modules.manifest.application.command;

import com.courier.modules.manifest.domain.FuelType;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.modules.manifest.domain.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code expectedVersion} guards a 409, same convention as {@code UpdateFreightFactorCommand}. */
public record UpdateVehicleCommand(
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
        Long expectedVersion
) {
}
