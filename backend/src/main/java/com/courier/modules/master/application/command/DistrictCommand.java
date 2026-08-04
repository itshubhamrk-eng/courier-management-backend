package com.courier.modules.master.application.command;

import java.util.UUID;

/** Input to create or update a district. See {@link CountryCommand} for the code/version rule. */
public record DistrictCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID stateId,
        Long expectedVersion
) {
}
