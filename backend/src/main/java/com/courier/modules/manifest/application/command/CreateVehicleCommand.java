package com.courier.modules.manifest.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateVehicleCommand(
        String vehicleNumber,
        UUID vehicleTypeId,
        BigDecimal capacityKg,
        String remarks
) {
}
