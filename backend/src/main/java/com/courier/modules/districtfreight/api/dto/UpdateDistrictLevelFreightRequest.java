package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/district-level-freight/{id}}. Full replacement of the
 * editable fields. {@code status} has its own endpoints. {@code version} is required.
 */
@Schema(name = "UpdateDistrictLevelFreightRequest",
        description = "Full replacement of a District Level Freight row's editable fields")
public record UpdateDistrictLevelFreightRequest(

        @NotNull UUID branchId,
        @NotNull UUID districtId,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate1To15,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate16To50,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate51To100,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate101To1000,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate1001To1500,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal rate1501To2000,

        Boolean odaApplicable,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal odaCharge,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
