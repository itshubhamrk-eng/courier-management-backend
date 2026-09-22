package com.courier.modules.crossing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/hub-operations/exceptions/{id}/resolve}. */
@Schema(name = "ResolveExceptionRequest", description = "Close an open Hub Operations exception")
public record ResolveExceptionRequest(@Size(max = 500) String resolutionRemarks) {
}
