package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/users/{id}/reset-password}. Admin action: sets a new
 * password without the old one. Subject to the same password policy as any other.
 */
@Schema(name = "ResetPasswordRequest", description = "Admin password reset")
public record ResetPasswordRequest(

        @NotBlank @Size(min = 8, max = 72) String newPassword,

        @Schema(description = "Flag the user to change it at next login (advisory)",
                defaultValue = "true")
        Boolean mustChangeOnNextLogin
) {

    public boolean mustChange() {
        return mustChangeOnNextLogin == null || mustChangeOnNextLogin;
    }
}
