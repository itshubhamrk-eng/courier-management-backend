package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of {@code PUT /api/v1/master/vehicle-types/{id}}. */
@Schema(name = "UpdateVehicleTypeRequest", description = "Full replacement of a vehicle type's editable fields")
public record UpdateVehicleTypeRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal capacityKg,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal capacityCft,
        @Positive Integer wheelCount,
        Boolean requiresPermit,
        @NotNull @PositiveOrZero Long version
) {
}
