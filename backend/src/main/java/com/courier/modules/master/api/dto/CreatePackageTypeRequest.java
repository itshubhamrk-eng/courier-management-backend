package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of {@code POST /api/v1/master/package-types}. */
@Schema(name = "CreatePackageTypeRequest", description = "New package type")
public record CreatePackageTypeRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "PARCEL") String code,
        @NotBlank @Size(max = 150) @Schema(example = "Parcel") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @Schema(description = "Documents are rated on a flat slab and skip dimension capture")
        Boolean documentType,
        Boolean fragileByDefault,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal maxWeightKg,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultLengthCm,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultWidthCm,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal defaultHeightCm
) {
}
