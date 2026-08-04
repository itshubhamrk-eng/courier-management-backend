package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of {@code PUT /api/v1/master/weight-slabs/{id}}. */
@Schema(name = "UpdateWeightSlabRequest", description = "Full replacement of a weight slab's editable fields")
public record UpdateWeightSlabRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal minWeight,
        @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal maxWeight,
        WeightUnit weightUnit,
        @NotNull @PositiveOrZero Long version
) {
}
