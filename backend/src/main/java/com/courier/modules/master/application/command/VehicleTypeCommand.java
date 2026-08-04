package com.courier.modules.master.application.command;

import java.math.BigDecimal;

/** Input to create or update a vehicle type. See {@link CountryCommand} for the code/version rule. */
public record VehicleTypeCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        BigDecimal capacityKg,
        BigDecimal capacityCft,
        Integer wheelCount,
        Boolean requiresPermit,
        Long expectedVersion
) {
}
