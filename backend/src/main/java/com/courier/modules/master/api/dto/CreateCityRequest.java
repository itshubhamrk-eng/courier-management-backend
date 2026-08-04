package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /api/v1/master/cities}. */
@Schema(name = "CreateCityRequest", description = "New city within a district")
public record CreateCityRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "PUNE") String code,
        @NotBlank @Size(max = 150) @Schema(example = "Pune") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID districtId,
        Boolean metro,
        @Pattern(regexp = "^$|^(?i)TIER_[1-4]$", message = "must be TIER_1 to TIER_4")
        @Schema(example = "TIER_1") String cityTier
) {
}
