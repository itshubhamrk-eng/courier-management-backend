package com.courier.modules.master.application.command;

import java.util.UUID;

/** Input to create or update a state. See {@link CountryCommand} for the code/version rule. */
public record StateCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID countryId,
        String gstStateCode,
        Long expectedVersion
) {
}
