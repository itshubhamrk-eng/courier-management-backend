package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.CrossingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/crossings/{id}/status}. */
@Schema(name = "UpdateCrossingStatusRequest", description = "Move a crossing to a new status")
public record UpdateCrossingStatusRequest(
        @NotNull CrossingStatus status,
        @Size(max = 500) String remarks
) {
}
