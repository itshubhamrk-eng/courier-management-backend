package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Opens a normal courier session for someone whose identity another system has already proven.
 *
 * <p>Used by the OCCPL platform sign-in: the caller has verified the person's token and asked the platform
 * whether they may use this company, so no password is checked here. Everything after that is the same as a
 * password login — session, tokens, permissions — so the rest of the app cannot tell the difference.
 *
 * <p>Deliberately never signs a platform-scoped account (SUPER_ADMIN, PLATFORM_ADMIN) in: those stay
 * password-only, so a platform user who happens to share an email with one cannot take it over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedSignInService {

    private final UserRepository userRepository;
    private final CompanyDirectoryPort companyDirectory;
    private final UserPermissionsPort userPermissionsPort;
    private final SessionService sessionService;
    private final TokenIssuer tokenIssuer;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserProvisioningService userProvisioningService;
    private final LoginAttemptService loginAttemptService;

    /**
     * @param rolesIfNew roles given only when the account has to be created; an existing account keeps its roles
     */
    public record Command(UUID companyId, String email, String firstName, String lastName, Set<Role> rolesIfNew,
                          String ipAddress, String userAgent, SessionService.DeviceInfo device) {
    }

    public AuthService.AuthResult signIn(Command command) {
        CompanyDirectoryPort.CompanyRef company = companyDirectory.findById(command.companyId())
                .filter(CompanyDirectoryPort.CompanyRef::active)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.COMPANY_INACTIVE, "Unknown or inactive company"));
        UUID companyId = company.id();
        CompanyContext.setCompanyId(companyId);

        String email = User.normaliseEmail(command.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            try {
                userProvisioningService.provisionPlatformUser(new UserProvisioningService.NewPlatformUserCommand(
                        companyId, email, command.firstName(), command.lastName(), command.rolesIfNew()));
            } catch (DuplicateResourceException raced) {
                // Two first sign-ins at the same moment: the other one created it.
            }
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, "Sign-in is not available."));
        }

        if (user.getRoles().stream().anyMatch(Role::isPlatformScoped)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Sign-in is not available for this account.");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled. Contact your administrator.");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED, "This account is locked. Contact your administrator.");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            // The platform has proven the address, which is what the activation link is for.
            user.markEmailVerified();
            user = userRepository.save(user);
        }

        Duration refreshTtl = jwtTokenProvider.refreshTokenTtl();
        UserSession session = sessionService.openSession(user.getId(), refreshTtl, false, command.device());
        TokenIssuer.TokenPair tokens = tokenIssuer.issueForNewSession(
                user, session, refreshTtl, command.ipAddress(), command.userAgent());

        try {
            loginAttemptService.recordSuccess(user.getId(), session.getId(), command.ipAddress(), command.userAgent());
        } catch (org.springframework.dao.ConcurrencyFailureException
                 | com.courier.shared.exception.CompanyIsolationException e) {
            // Bookkeeping only; the session and tokens are already issued (same stance as a password login).
            log.warn("Sign-in bookkeeping skipped for user {}: {}", user.getId(), e.getMessage());
        }

        log.info("User {} signed in to company {} through the platform (session {})",
                user.getId(), companyId, session.getId());
        return new AuthService.AuthResult(user, tokens, session, company,
                userPermissionsPort.resolveEffectivePermissions(user.getId(), user.roleNames()));
    }
}
