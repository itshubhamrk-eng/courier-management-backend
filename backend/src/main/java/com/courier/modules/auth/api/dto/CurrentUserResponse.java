package com.courier.modules.auth.api.dto;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in user's own profile.
 *
 * <p>Built from a fresh database read rather than from token claims, so a role
 * change takes effect immediately instead of at the next token refresh.
 */
@Schema(name = "CurrentUserResponse", description = "Profile of the authenticated user")
public record CurrentUserResponse(
        UUID id,
        UUID companyId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String phone,
        String status,
        Set<String> roles,
        boolean emailVerified,
        Instant lastLoginAt,

        @Schema(description = "Display name of the user's company, for session branding")
        String companyName,

        @Schema(description = "Logo URL of the user's company; null when not set")
        String companyLogo
) {

    public static CurrentUserResponse from(User user, CompanyDirectoryPort.CompanyRef company) {
        return new CurrentUserResponse(
                user.getId(),
                user.getCompanyId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.displayName(),
                user.getPhone(),
                user.getStatus().name(),
                user.roleNames(),
                user.isEmailVerified(),
                user.getLastLoginAt(),
                company != null ? company.name() : null,
                company != null ? company.logo() : null);
    }
}
