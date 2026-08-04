package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/users/{id}/lock}. The reason is audited. */
@Schema(name = "LockUserRequest", description = "Why the user is being locked")
public record LockUserRequest(
        @NotBlank @Size(max = 500)
        @Schema(example = "Suspected credential sharing; pending investigation")
        String reason
) {
}
