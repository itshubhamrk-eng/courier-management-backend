package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/users/{id}/change-password}. Self-service: a user changes
 * their own password and must supply the current one. An admin who does not know the old
 * password uses reset-password instead.
 */
@Schema(name = "ChangePasswordRequest", description = "Self-service password change")
public record ChangePasswordRequest(

        @NotBlank String currentPassword,

        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
