package com.courier.modules.master.application.command;

import java.util.UUID;

/**
 * Input to create or update a pincode. See {@link CountryCommand} for the code/version rule.
 *
 * <p>{@code code} is the postal code itself; {@code name} is the post office label.
 */
public record PincodeCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        UUID areaId,
        Boolean serviceable,
        Boolean codAvailable,
        Boolean prepaidAvailable,
        Boolean pickupAvailable,
        String zone,
        Boolean odaApplicable,
        Long expectedVersion
) {
}
