package com.courier.modules.freight.application.command;

import java.math.BigDecimal;

public record CreateFreightFactorCommand(
        BigDecimal fromKm,
        BigDecimal toKm,
        BigDecimal fromWeight,
        BigDecimal toWeight,
        BigDecimal factor
) {
}
