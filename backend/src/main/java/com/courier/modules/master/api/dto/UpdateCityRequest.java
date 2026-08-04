package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code PUT /api/v1/master/cities/{id}}. */
@Schema(name = "UpdateCityRequest", description = "Full replacement of a city's editable fields")
public record UpdateCityRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID districtId,
        Boolean metro,
        @Pattern(regexp = "^$|^(?i)TIER_[1-4]$") String cityTier,
        @NotNull @PositiveOrZero Long version
) {
}
