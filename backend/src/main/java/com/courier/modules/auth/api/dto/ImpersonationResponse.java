package com.courier.modules.auth.api.dto;

import com.courier.modules.auth.application.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

/**
 * A short-lived, company-scoped session opened by a SUPER_ADMIN.
 *
 * <p>Deliberately not {@link LoginResponse}-shaped: there is no refresh token (see
 * {@code JwtTokenProvider#generateImpersonationAccessToken}) and no session id — this
 * is not tracked in the device/session list, it simply expires.
 */
@Schema(name = "ImpersonationResponse", description = "Issued impersonation token and the company being impersonated")
public record ImpersonationResponse(

        @Schema(description = "Short-lived bearer token scoped to the target company. "
                + "No refresh token is issued — it hard-expires rather than being extendable.")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresIn,

        @Schema(description = "The company's own admin user this session acts as")
        UUID userId,

        UUID companyId,
        String email,
        String displayName,
        Set<String> roles,
        String companyName,
        String companyLogo,

        @Schema(description = "The SUPER_ADMIN who opened this session")
        UUID impersonatorId,
        String impersonatorEmail
) {

    public static ImpersonationResponse from(AuthService.ImpersonationResult result) {
        var company = result.company();
        var user = result.impersonatedUser();
        return new ImpersonationResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenTtl().toSeconds(),
                user.getId(),
                user.getCompanyId(),
                user.getEmail(),
                user.displayName(),
                user.roleNames(),
                company != null ? company.name() : null,
                company != null ? company.logo() : null,
                result.impersonator().getId(),
                result.impersonator().getEmail());
    }
}
