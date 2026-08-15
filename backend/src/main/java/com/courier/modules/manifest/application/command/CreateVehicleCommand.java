package com.courier.modules.manifest.application.command;

import com.courier.modules.manifest.domain.FuelType;
import com.courier.modules.manifest.domain.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateVehicleCommand(
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
        UUID branchId,
        String remarks
) {
}
