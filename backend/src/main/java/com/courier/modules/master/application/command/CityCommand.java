package com.courier.modules.master.application.command;

import java.util.UUID;

/** Input to create or update a city. See {@link CountryCommand} for the code/version rule. */
public record CityCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID districtId,
        Boolean metro,
        String cityTier,
        Long expectedVersion
) {
}
