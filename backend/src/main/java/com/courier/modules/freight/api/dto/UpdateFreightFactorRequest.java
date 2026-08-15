package com.courier.modules.freight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Body of {@code PUT /api/v1/freight-factors/{id}}. Full replacement of the editable
 * fields. {@code status} has its own endpoints. {@code version} is required.
 */
@Schema(name = "UpdateFreightFactorRequest", description = "Full replacement of a freight factor cell's editable fields")
public record UpdateFreightFactorRequest(

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromKm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal toKm,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromWeight,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal toWeight,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal factor,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
