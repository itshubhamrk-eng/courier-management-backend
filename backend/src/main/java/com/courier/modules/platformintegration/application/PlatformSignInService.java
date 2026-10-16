package com.courier.modules.platformintegration.application;

import com.courier.modules.auth.application.AuthService;
import com.courier.modules.auth.application.SessionService;
import com.courier.modules.auth.application.TrustedSignInService;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Sign in with the OCCPL platform": turns a platform access token into a normal courier session.
 *
 * <p>Every refusal after the token itself is valid answers the same 403, so the endpoint does not reveal
 * which companies exist here or who belongs to them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
public class PlatformSignInService {

    static final String PLATFORM_COMPANY_ADMIN = "COMPANY_ADMIN";

    private final JwtDecoder platformJwtDecoder;
    private final CompanyRepository companyRepository;
    private final CoreAccessClient core;
    private final TrustedSignInService trustedSignIn;
    private final PlatformIntegrationProperties properties;

    public record Request(String accessToken, UUID platformCompanyId, String ipAddress, String userAgent,
                          SessionService.DeviceInfo device) {
    }

    public AuthService.AuthResult signIn(Request request) {
        Jwt jwt = verify(request.accessToken());
        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();

        var company = companyRepository.findByExternalCompanyId(request.platformCompanyId())
                .orElseThrow(PlatformSignInService::notAvailable);

        CoreAccessClient.Decision decision;
        try {
            decision = core.check(request.platformCompanyId(), subject);
        } catch (IllegalStateException e) {
            log.error("Platform sign-in could not ask the platform about company {}: {}",
                    request.platformCompanyId(), e.getMessage());
            throw new com.courier.shared.exception.BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Sign-in through the platform is temporarily unavailable. Try again shortly.");
        }
        if (!decision.allowed()) {
            log.info("Platform sign-in refused for company {} ({})", request.platformCompanyId(), decision.reason());
            throw notAvailable();
        }

        return trustedSignIn.signIn(new TrustedSignInService.Command(
                company.getCompanyId(), email, firstName(jwt), lastName(jwt), rolesFor(decision.roles()),
                request.ipAddress(), request.userAgent(), request.device()));
    }

    private Jwt verify(String token) {
        Jwt jwt;
        try {
            jwt = platformJwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new UnauthorizedException(ErrorCode.UNAUTHENTICATED, "The platform sign-in has expired or is invalid.");
        }
        boolean fromOurWebClient = properties.webClientId() != null
                && properties.webClientId().equals(jwt.getClaimAsString("azp"));
        // A service token (client_id claim, no person) or a token minted for another app must never sign anyone in.
        if (!fromOurWebClient || jwt.getClaimAsString("client_id") != null
                || jwt.getClaimAsString("email") == null || jwt.getClaimAsString("email").isBlank()
                || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                || jwt.getSubject() == null) {
            throw new UnauthorizedException(ErrorCode.UNAUTHENTICATED, "The platform sign-in has expired or is invalid.");
        }
        return jwt;
    }

    /** Platform company admins become courier company admins; everyone else starts with the lowest role. */
    static Set<Role> rolesFor(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains(PLATFORM_COMPANY_ADMIN)
                ? EnumSet.of(Role.COMPANY_ADMIN)
                : EnumSet.of(Role.VIEWER);
    }

    private static String firstName(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        if (given != null && !given.isBlank()) {
            return given;
        }
        String name = jwt.getClaimAsString("name");
        return name == null || name.isBlank() ? null : name.trim().split("\\s+", 2)[0];
    }

    private static String lastName(Jwt jwt) {
        String family = jwt.getClaimAsString("family_name");
        if (family != null && !family.isBlank()) {
            return family;
        }
        String name = jwt.getClaimAsString("name");
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+", 2);
        return parts.length == 2 ? parts[1] : null;
    }

    private static ForbiddenException notAvailable() {
        return new ForbiddenException(ErrorCode.ACCESS_DENIED, "Sign-in through the platform is not available for this account.");
    }
}
