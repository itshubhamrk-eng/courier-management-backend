package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of {@code POST /api/v1/master/vehicle-types}. */
@Schema(name = "CreateVehicleTypeRequest", description = "New vehicle type")
public record CreateVehicleTypeRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "TRUCK") String code,
        @NotBlank @Size(max = 150) @Schema(example = "Truck") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3)
        @Schema(description = "Payload capacity in kilograms") BigDecimal capacityKg,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3)
        @Schema(description = "Load volume in cubic feet") BigDecimal capacityCft,
        @Positive Integer wheelCount,
        Boolean requiresPermit
) {
}
