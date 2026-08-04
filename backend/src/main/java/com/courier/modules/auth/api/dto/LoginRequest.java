package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Credentials plus the company to authenticate against.
 *
 * <p>{@code companyId} is required today. {@code companyCode} is accepted by the API
 * but cannot be resolved until {@code modules/company} ships — the field exists so
 * the contract does not have to change then, and a caller who sends it gets an
 * explicit message rather than a confusing credential error.
 */
@Schema(name = "LoginRequest", description = "Sign in to a company")
public record LoginRequest(

        @Schema(description = "Company to sign in to. Required until slug lookup ships.",
                example = "018f3a5c-1b2d-7e4f-9a8b-2c3d4e5f6a7b")
        UUID companyId,

        @Schema(description = "Reserved. Not resolvable until the company module ships.",
                example = "acme-couriers")
        String companyCode,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        @Schema(example = "ops@acme.test")
        String email,

        // No @Size minimum: the policy is enforced when a password is *set*, and
        // applying it at login would leak the policy to an attacker and reject
        // legitimate users whose password predates a policy change.
        @NotBlank(message = "Password is required")
        @Schema(example = "correct-horse-battery-staple")
        String password,

        @Schema(description = "Extends the refresh token and session to app.auth.remember-me-duration",
                example = "false")
        boolean rememberMe,

        @Size(max = 128)
        @Schema(description = "Optional client-supplied device identifier, for the device list",
                example = "web-chrome-macos")
        String deviceId,

        @Size(max = 150)
        @Schema(example = "Chrome on macOS")
        String deviceName
) {
}
