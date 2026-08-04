package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of {@code PUT /api/v1/master/package-types/{id}}. */
@Schema(name = "UpdatePackageTypeRequest", description = "Full replacement of a package type's editable fields")
public record UpdatePackageTypeRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        Boolean documentType,
        Boolean fragileByDefault,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal maxWeightKg,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultLengthCm,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultWidthCm,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultHeightCm,
        @NotNull @PositiveOrZero Long version
) {
}
