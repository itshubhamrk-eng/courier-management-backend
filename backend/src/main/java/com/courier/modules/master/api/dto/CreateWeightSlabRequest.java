package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/v1/master/weight-slabs}.
 *
 * <p>The band is <b>half-open</b>: {@code [minWeight, maxWeight)}. A 1 kg parcel falls in
 * 1–5, not 0–1. Two active slabs of the same unit may touch but never overlap; an overlap
 * is refused with 422.
 */
@Schema(name = "CreateWeightSlabRequest", description = "New weight slab, [min, max)")
public record CreateWeightSlabRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "SLAB_1_5KG") String code,
        @NotBlank @Size(max = 150) @Schema(example = "1 to 5 kg") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3)
        @Schema(example = "1.000", description = "Inclusive") BigDecimal minWeight,
        @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3)
        @Schema(example = "5.000", description = "Exclusive") BigDecimal maxWeight,
        WeightUnit weightUnit
) {
}
