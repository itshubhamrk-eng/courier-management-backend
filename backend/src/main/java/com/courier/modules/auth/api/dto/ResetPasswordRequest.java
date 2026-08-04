package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetPasswordRequest", description = "Complete a password reset")
public record ResetPasswordRequest(

        @NotBlank(message = "Token is required")
        @Schema(description = "The single-use token from the emailed link")
        String token,

        // Bean validation only bounds the length here; the real rules live in
        // PasswordPolicy so they are configurable and testable in one place.
        @NotBlank(message = "New password is required")
        @Size(max = 200, message = "Password is too long")
        String newPassword
) {
}
