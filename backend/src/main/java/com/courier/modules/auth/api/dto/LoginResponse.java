package com.courier.modules.auth.api.dto;

import com.courier.modules.auth.application.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

/**
 * Issued token pair plus the minimum profile a client needs to render a session.
 *
 * <p>Returned by both login and refresh so clients have one shape to handle.
 */
@Schema(name = "LoginResponse", description = "Issued tokens and the signed-in user")
public record LoginResponse(

        @Schema(description = "Short-lived bearer token for the Authorization header")
        String accessToken,

        @Schema(description = "Used once against /auth/refresh; rotated on every use")
        String refreshToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresIn,

        @Schema(description = "Refresh token lifetime in seconds", example = "604800")
        long refreshExpiresIn,

        @Schema(description = "Session this pair belongs to; appears in the device list")
        UUID sessionId,

        UUID userId,
        UUID companyId,
        String email,
        String displayName,
        Set<String> roles,

        @Schema(description = "The branch this account is staffed at, if any — null for "
                + "a company admin or a user with no branch assignment")
        UUID branchId,

        @Schema(description = "The hub this account is staffed at, if any")
        UUID hubId,

        @Schema(description = "Display name of the signed-in company, for session branding")
        String companyName,

        @Schema(description = "Logo URL of the signed-in company; null when not set")
        String companyLogo
) {

    public static LoginResponse from(AuthService.AuthResult result) {
        var company = result.company();
        return new LoginResponse(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                "Bearer",
                result.tokens().accessTokenTtl().toSeconds(),
                result.tokens().refreshTokenTtl().toSeconds(),
                result.session().getId(),
                result.user().getId(),
                result.user().getCompanyId(),
                result.user().getEmail(),
                result.user().displayName(),
                result.user().roleNames(),
                result.user().getBranchId(),
                result.user().getHubId(),
                company != null ? company.name() : null,
                company != null ? company.logo() : null);
    }
}
