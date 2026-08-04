package com.courier.modules.master.application.command;

import java.math.BigDecimal;

/** Input to create or update a package type. See {@link CountryCommand} for the code/version rule. */
public record PackageTypeCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        Boolean documentType,
        Boolean fragileByDefault,
        BigDecimal maxWeightKg,
        BigDecimal defaultLengthCm,
        BigDecimal defaultWidthCm,
        BigDecimal defaultHeightCm,
        Long expectedVersion
) {
}
