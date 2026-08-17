package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ReplyRequest", description = "Add a reply or, for staff, an internal note")
public record ReplyRequest(
        @NotBlank @Size(max = 8000) String body,
        @Schema(description = "Staff-only, invisible to the requester") boolean internalNote
) {
}
