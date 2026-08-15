package com.courier.modules.freight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/v1/freight-factors}. {@code COMPANY_ADMIN} only. A new cell
 * always starts ACTIVE — status has its own lifecycle endpoints.
 */
@Schema(name = "CreateFreightFactorRequest", description = "New freight factor grid cell within the caller's company")
public record CreateFreightFactorRequest(

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(example = "0") BigDecimal fromKm,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        @Schema(example = "100") BigDecimal toKm,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(example = "0") BigDecimal fromWeight,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        @Schema(example = "10") BigDecimal toWeight,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        @Schema(description = "freight = factor * weight", example = "12.50") BigDecimal factor
) {
}
