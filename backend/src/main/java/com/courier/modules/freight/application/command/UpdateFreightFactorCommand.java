package com.courier.modules.freight.application.command;

import java.math.BigDecimal;

/** {@code expectedVersion} guards a 409, same convention as {@code UpdateRateCommand}. */
public record UpdateFreightFactorCommand(
        BigDecimal fromKm,
        BigDecimal toKm,
        BigDecimal fromWeight,
        BigDecimal toWeight,
        BigDecimal factor,
        Long expectedVersion
) {
}
