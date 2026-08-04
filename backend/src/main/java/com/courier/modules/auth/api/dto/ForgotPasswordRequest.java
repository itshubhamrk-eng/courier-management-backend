package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Starts a password reset.
 *
 * <p>The endpoint always responds 200 regardless of whether the address exists —
 * see {@code PasswordService#forgotPassword}.
 */
@Schema(name = "ForgotPasswordRequest", description = "Request a password reset link")
public record ForgotPasswordRequest(

        @NotNull(message = "companyId is required")
        UUID companyId,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email
) {
}
