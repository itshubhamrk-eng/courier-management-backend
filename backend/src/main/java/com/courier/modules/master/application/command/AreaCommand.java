package com.courier.modules.master.application.command;

import java.util.UUID;

/** Input to create or update an area. See {@link CountryCommand} for the code/version rule. */
public record AreaCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID cityId,
        Long expectedVersion
) {
}
