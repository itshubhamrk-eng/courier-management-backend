package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /api/v1/master/states}. The country must exist in the company and be active. */
@Schema(name = "CreateStateRequest", description = "New state within a country")
public record CreateStateRequest(

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "MH") String code,

        @NotBlank @Size(max = 150) @Schema(example = "Maharashtra") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,

        @NotNull UUID countryId,
        @Size(max = 4) @Schema(description = "GST state code, text so a leading zero survives",
                example = "27") String gstStateCode
) {
}
