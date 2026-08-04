package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code PUT /api/v1/master/areas/{id}}. */
@Schema(name = "UpdateAreaRequest", description = "Full replacement of an area's editable fields")
public record UpdateAreaRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID cityId,
        @NotNull @PositiveOrZero Long version
) {
}
