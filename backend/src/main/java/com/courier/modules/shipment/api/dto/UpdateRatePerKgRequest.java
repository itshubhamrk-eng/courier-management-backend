package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateRatePerKgRequest(
        @NotNull Long version,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal ratePerKg
) {
}
