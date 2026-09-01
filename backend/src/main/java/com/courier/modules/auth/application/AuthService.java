package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.BranchDirectoryPort;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.LoginFailureReason;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.auth.infrastructure.security.AuthUserDetails;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.JwtTokenProvider;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The authentication use cases: login, refresh, logout, and current-user lookup.
 *
 * <p>Credential verification is delegated to Spring Security's
 * {@code AuthenticationManager} rather than hand-rolled. That is not ceremony:
 * {@code DaoAuthenticationProvider} performs a dummy BCrypt comparison when the
 * user does not exist, which removes the timing oracle that would otherwise let an
 * attacker enumerate valid email addresses by response latency.
 *
 * <p>All credential failures — unknown email, wrong password — surface as the
 * identical {@code 401 INVALID_CREDENTIALS}. Only lock, disable and verification
 * states differ, and those are states the legitimate user must be told about.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CompanyDirectoryPort companyDirectory;
    private final BranchDirectoryPort branchDirectory;
    private final SessionService sessionService;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final TokenRevocationService tokenRevocationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties properties;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Deliberately short — this is an impersonation session, not a normal login, and
     * carries no refresh token to extend it. See {@link #impersonateCompany}.
     */
    private static final Duration IMPERSONATION_TTL = Duration.ofMinutes(15);

    @PostConstruct
    void validateConfiguration() {
        // Fails the context rather than letting a nonsensical policy reach production.
        properties.validate();
    }

    // ------------------------------------------------------------------- login

    /**
     * Authenticates a user within a company and opens a session.
     *
     * <p>Ordering is deliberate: the company is validated and bound <em>before</em>
     * any user lookup, so the Hibernate company filter is active and a user in
     * another company is not merely rejected but invisible.
     *
     * <p><b>Deliberately not {@code @Transactional}</b> — every DB-writing step this
     * method calls already manages its own transaction independently
     * ({@code sessionService.openSession}, {@code tokenIssuer.issueForNewSession},
     * {@code loginAttemptService.recordSuccess}/{@code recordFailure}, the last two
     * {@code REQUIRES_NEW} specifically), so a blanket transaction here was never
     * load-bearing for atomicity — it only ever held one Hikari connection open for
     * this whole method's duration, including the {@code authenticationManager
     * .authenticate(...)} call below, which does BCrypt(12) password verification: a
     * deliberately CPU-expensive step (see {@code SecurityConfig}), not a DB one.
     * Found running a k6 load test: at ~60+ concurrent logins that connection-hold
     * time (not real DB load) exhausted the whole pool and collapsed every endpoint
     * sharing it, not just login — see {@code perf-tests/ISSUES.md} ISSUE-005 for the
     * full evidence (100 VUs went from ~100% request failure to 0% purely from a
     * larger pool, before this fix even existed — this closes the actual root cause
     * rather than just buying more headroom against it).
     */
    public AuthResult login(LoginCommand command) {
        String email = User.normaliseEmail(command.email());
        UUID companyId = resolveCompany(command, email);

        // Bind before the first repository call — everything below is company-scoped.
        CompanyContext.setCompanyId(companyId);

        loginAttemptService.assertNotThrottled(email, command.ipAddress());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, command.password()));

        } catch (LockedException e) {
            User locked = userRepository.findByEmail(email).orElse(null);
            loginAttemptService.recordFailure(companyId, null, email,
                    LoginFailureReason.ACCOUNT_LOCKED, command.ipAddress(), command.userAgent());
            throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED, lockMessage(locked));

        } catch (DisabledException e) {
            loginAttemptService.recordFailure(companyId, null, email,
                    LoginFailureReason.ACCOUNT_DISABLED, command.ipAddress(), command.userAgent());
            throw new ForbiddenException(ErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled. Contact your administrator.");

        } catch (CredentialsExpiredException e) {
            loginAttemptService.recordFailure(companyId, null, email,
                    LoginFailureReason.PASSWORD_EXPIRED, command.ipAddress(), command.userAgent());
            throw new ForbiddenException(ErrorCode.PASSWORD_EXPIRED,
                    "Your password has expired. Reset it to continue.");

        } catch (BadCredentialsException e) {
            // Look the user up only to advance the lock counter. The response is
            // identical whether or not this finds anything.
            User candidate = userRepository.findByEmail(email).orElse(null);
            try {
                loginAttemptService.recordFailure(companyId, candidate == null ? null : candidate.getId(), email,
                        candidate == null ? LoginFailureReason.UNKNOWN_USER : LoginFailureReason.BAD_PASSWORD,
                        command.ipAddress(), command.userAgent());
            } catch (RuntimeException bookkeeping) {
                // This lookup runs outside any @Transactional boundary (see this
                // method's own class javadoc), so the company filter that would
                // normally hide another company's row is not guaranteed active —
                // `candidate` can resolve to a platform-role user genuinely anchored
                // to a different company than the one this login attempt resolved
                // (e.g. a SUPER_ADMIN signing in on a company-mapped domain). Bumping
                // that row's lockout counter then trips CompanyIsolationException
                // (Sev-1, logged here at ERROR for the same alerting reason) — but a
                // bookkeeping failure must never turn an otherwise-correct "invalid
                // credentials" response into a 500, same principle as recordSuccess's
                // own race-swallow below.
                log.error("Login-failure bookkeeping blocked for {} (company {}): {}",
                        email, companyId, bookkeeping.getMessage(), bookkeeping);
            }
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        User user = ((AuthUserDetails) authentication.getPrincipal()).user();

        // Checked only after the password verifies, so an unverified account is not
        // disclosed to someone who does not already hold the credentials.
        if (!user.isEmailVerified()) {
            emailVerificationService.reissueIfDue(user);
            loginAttemptService.recordFailure(companyId, user.getId(), email,
                    LoginFailureReason.EMAIL_NOT_VERIFIED, command.ipAddress(), command.userAgent());
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Verify your email address to sign in. We have sent you a new link.");
        }

        Duration refreshTtl = command.rememberMe()
                ? properties.getRememberMeDuration()
                : jwtTokenProvider.refreshTokenTtl();

        UserSession session = sessionService.openSession(
                user.getId(), refreshTtl, command.rememberMe(), command.device());

        TokenIssuer.TokenPair tokens = tokenIssuer.issueForNewSession(
                user, session, refreshTtl, command.ipAddress(), command.userAgent());

        // Best-effort bookkeeping (last-login timestamp, failed-attempt reset, audit
        // trail) — never lets a race on the User row's own optimistic lock turn an
        // already-successful login (credentials verified, session and tokens already
        // issued above) into a 409 the client never asked for. Found running a k6 load
        // test with more virtual users than distinct accounts, so two VUs legitimately
        // logged into the same account within the same millisecond — a real client can
        // hit this too (e.g. two browser tabs signing in at once).
        try {
            loginAttemptService.recordSuccess(user.getId(), session.getId(), command.ipAddress(), command.userAgent());
        } catch (org.springframework.dao.ConcurrencyFailureException e) {
            // Covers both an optimistic-lock version mismatch and a genuine InnoDB
            // deadlock/lock-wait timeout on the users row — both are real outcomes of
            // two concurrent logins to the same account (found running a k6 load
            // test), not just the optimistic case.
            log.warn("Login bookkeeping lost a race for user {} (concurrent login to the same account) "
                    + "— the login itself still succeeded", user.getId());
        } catch (com.courier.shared.exception.CompanyIsolationException e) {
            // Same scenario the BadCredentials catch above already documents: a
            // platform-role user (SUPER_ADMIN/PLATFORM_ADMIN) genuinely anchored to a
            // different company than the one this login resolved (companyId came from
            // a companyCode the caller supplied/had prefilled, not from the user's own
            // row). Password verified correctly and the session/tokens are already
            // issued — found in prod 2026-09-02 when this crashed SUPER_ADMIN login as
            // an unhandled 500 instead of failing open the same way the bad-password
            // path already does.
            log.error("Login bookkeeping blocked by company isolation for user {} (resolved company {}) "
                    + "— the login itself still succeeded", user.getId(), companyId, e);
        }

        log.info("User {} signed in to company {} (session {}, rememberMe={})",
                user.getId(), companyId, session.getId(), command.rememberMe());

        return new AuthResult(user, tokens, session, companyBrand(companyId));
    }

    private String lockMessage(User user) {
        if (user != null && user.isTemporarilyLocked()) {
            return "Account temporarily locked after repeated failed attempts. Try again after %s."
                    .formatted(user.getLockedUntil());
        }
        return "This account is locked. Contact your administrator.";
    }

    /**
     * Validates the company through the port.
     *
     * <p>Either identifier works: {@code companyCode} — what a user actually types —
     * or the {@code companyId} the token will carry. An unknown code and an inactive
     * company give the same answer, so neither confirms that a company exists.
     */
    private UUID resolveCompany(LoginCommand command, String email) {
        if (command.companyId() == null) {
            if (command.companyCode() != null && !command.companyCode().isBlank()) {
                return companyDirectory.findByCode(command.companyCode())
                        .filter(CompanyDirectoryPort.CompanyRef::active)
                        .map(CompanyDirectoryPort.CompanyRef::id)
                        .orElseThrow(() -> new ForbiddenException(
                                ErrorCode.COMPANY_INACTIVE, "Unknown or inactive company"));
            }
            // No company supplied: only a platform-level admin may sign in this way.
            // Their home company is derived server-side; anyone else fails as a normal
            // bad credential, so this reveals nothing about ordinary accounts.
            return resolvePlatformCompany(email);
        }

        return companyDirectory.findById(command.companyId())
                .filter(CompanyDirectoryPort.CompanyRef::active)
                .map(CompanyDirectoryPort.CompanyRef::id)
                .orElseThrow(() -> new ForbiddenException(
                        ErrorCode.COMPANY_INACTIVE, "Unknown or inactive company"));
    }

    /** Roles that own the platform rather than a single company, so may sign in without one. */
    private static final Set<Role> PLATFORM_ROLES = EnumSet.of(Role.SUPER_ADMIN, Role.PLATFORM_ADMIN);

    /**
     * Resolves the home company of a platform admin signing in without a company code.
     *
     * <p>Runs with no company bound, so the cross-company lookup is intentional and
     * restricted to platform roles (see {@code UserRepository#findPlatformUsersByEmail}).
     * A non-platform email, or an ambiguous match, is indistinguishable from a wrong
     * password: it throws the same {@code 401 INVALID_CREDENTIALS}. The password itself
     * is still verified afterwards by the normal {@code AuthenticationManager} flow once
     * this company is bound.
     */
    private UUID resolvePlatformCompany(String email) {
        List<User> matches = userRepository.findPlatformUsersByEmail(email, PLATFORM_ROLES);
        if (matches.size() != 1) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        return matches.get(0).getCompanyId();
    }

    // ------------------------------------------------------------ impersonate

    /**
     * Opens a short-lived, company-scoped session on behalf of a SUPER_ADMIN — "login
     * as company" without knowing (or resetting) any of that company's passwords.
     *
     * <p>Three deliberate design choices, each pinned down by a direct product
     * decision rather than assumed:
     * <ol>
     *   <li><b>Step-up auth.</b> The caller is already authenticated as SUPER_ADMIN,
     *       but this re-verifies their own current password anyway — defence in depth
     *       against a hijacked or left-open SUPER_ADMIN session being used to reach
     *       into every company on the platform.</li>
     *   <li><b>Acts as the company's real {@code COMPANY_ADMIN}</b>, not a synthetic
     *       token still tagged as the super admin — so anything done while
     *       impersonating (e.g. "created by") resolves to a real user in that
     *       company's own table, not a dangling cross-company id.</li>
     *   <li><b>No refresh token.</b> {@link #IMPERSONATION_TTL} hard-caps the session;
     *       the frontend must fall back to the super admin's own stashed session (or
     *       force a fresh impersonation) once it expires, rather than silently
     *       extending a company-impersonating session for hours.</li>
     * </ol>
     *
     * <p>Every call is audited ({@link AuditAction#COMPANY_IMPERSONATED}) with both
     * identities — the acting super admin and the company user assumed.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ImpersonationResult impersonateCompany(UUID targetCompanyId, String password, String ipAddress) {
        AuthenticatedUser principal = SecurityUtils.requireCurrentUser();

        User superAdmin = userRepository.findByIdWithinCompany(principal.userId(), principal.companyId())
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_CREDENTIALS, "Session is no longer valid"));

        // Same email+IP throttle login uses — bounds repeated step-up guesses without
        // touching the super admin's own account-lock counters (a mistyped confirmation
        // must not be able to lock the super admin out of their own account).
        loginAttemptService.assertNotThrottled(superAdmin.getEmail(), ipAddress);

        if (!passwordEncoder.matches(password, superAdmin.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Incorrect password");
        }

        CompanyDirectoryPort.CompanyRef company = companyDirectory.findById(targetCompanyId)
                .filter(CompanyDirectoryPort.CompanyRef::active)
                .orElseThrow(() -> new ResourceNotFoundException("Company", targetCompanyId));

        List<User> admins = CompanyContext.runAs(targetCompanyId, () ->
                userRepository.findByCompanyIdAndRoleAndStatus(targetCompanyId, Role.COMPANY_ADMIN, UserStatus.ACTIVE));

        if (admins.isEmpty()) {
            throw new ResourceNotFoundException("This company has no active admin user to impersonate");
        }
        User target = admins.get(0);

        String accessToken = jwtTokenProvider.generateImpersonationAccessToken(
                target.getId(), targetCompanyId, target.getEmail(), target.roleNames(),
                target.getBranchId(), target.getHubId(), company.name(), company.logo(),
                superAdmin.getId(), superAdmin.getEmail(), IMPERSONATION_TTL);

        auditService.record(AuditAction.COMPANY_IMPERSONATED, "Company", targetCompanyId,
                Map.of("impersonatorId", superAdmin.getId().toString(),
                        "impersonatorEmail", superAdmin.getEmail(),
                        "impersonatedUserId", target.getId().toString(),
                        "impersonatedUserEmail", target.getEmail(),
                        "ipAddress", String.valueOf(ipAddress)));

        log.warn("SUPER_ADMIN {} ({}) started impersonating company {} ({}) as {} ({})",
                superAdmin.getId(), superAdmin.getEmail(), targetCompanyId, company.name(),
                target.getId(), target.getEmail());

        return new ImpersonationResult(target, company, accessToken, IMPERSONATION_TTL, superAdmin);
    }

    /** Outcome of {@link #impersonateCompany} or {@link #impersonateBranch}. */
    public record ImpersonationResult(User impersonatedUser,
                                      CompanyDirectoryPort.CompanyRef company,
                                      String accessToken,
                                      Duration accessTokenTtl,
                                      User impersonator) {
    }

    /**
     * Opens a short-lived, branch-scoped session on behalf of a COMPANY_ADMIN — "login as
     * branch" without knowing (or resetting) that branch manager's password.
     *
     * <p>Deliberately <b>no step-up password re-entry</b>, unlike {@link #impersonateCompany}:
     * the caller is already the company's own admin acting inside their own company (no
     * cross-tenant reach), and this is the product's own explicit call for this feature.
     * Every other safeguard from company impersonation still applies unchanged — a real
     * {@code BRANCH_MANAGER} of that branch is assumed (never a synthetic still-admin
     * token), the session issues no refresh token so it hard-expires at
     * {@link #IMPERSONATION_TTL}, and every call is audited
     * ({@link AuditAction#BRANCH_IMPERSONATED}) with both identities.
     */
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Transactional
    public ImpersonationResult impersonateBranch(UUID targetBranchId, String ipAddress) {
        AuthenticatedUser principal = SecurityUtils.requireCurrentUser();

        User companyAdmin = userRepository.findByIdWithinCompany(principal.userId(), principal.companyId())
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_CREDENTIALS, "Session is no longer valid"));

        BranchDirectoryPort.BranchRef branch = branchDirectory.findById(targetBranchId, principal.companyId())
                .filter(BranchDirectoryPort.BranchRef::active)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", targetBranchId));

        List<User> managers = userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(
                principal.companyId(), targetBranchId, Role.BRANCH_MANAGER, UserStatus.ACTIVE);

        if (managers.isEmpty()) {
            throw new ResourceNotFoundException("This branch has no active manager to log in as");
        }
        User target = managers.get(0);

        CompanyDirectoryPort.CompanyRef company = companyBrand(principal.companyId());

        String accessToken = jwtTokenProvider.generateImpersonationAccessToken(
                target.getId(), principal.companyId(), target.getEmail(), target.roleNames(),
                target.getBranchId(), target.getHubId(),
                company != null ? company.name() : null, company != null ? company.logo() : null,
                companyAdmin.getId(), companyAdmin.getEmail(), IMPERSONATION_TTL);

        auditService.record(AuditAction.BRANCH_IMPERSONATED, "Branch", targetBranchId,
                Map.of("impersonatorId", companyAdmin.getId().toString(),
                        "impersonatorEmail", companyAdmin.getEmail(),
                        "impersonatedUserId", target.getId().toString(),
                        "impersonatedUserEmail", target.getEmail(),
                        "branchName", branch.name(),
                        "ipAddress", String.valueOf(ipAddress)));

        log.warn("COMPANY_ADMIN {} ({}) started impersonating branch {} ({}) as {} ({})",
                companyAdmin.getId(), companyAdmin.getEmail(), targetBranchId, branch.name(),
                target.getId(), target.getEmail());

        return new ImpersonationResult(target, company, accessToken, IMPERSONATION_TTL, companyAdmin);
    }

    // ----------------------------------------------------------------- refresh

    /**
     * Rotates a refresh token and returns a new pair.
     *
     * <p>The company is bound from the token's own {@code cid} claim, so a refresh
     * cannot be redeemed against a different company even with a valid signature.
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        UUID companyId = jwtTokenProvider.parseRefreshToken(rawRefreshToken)
                .map(jwtTokenProvider::companyClaim)
                .filter(claim -> claim != null && !claim.isBlank())
                .map(UUID::fromString)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or has already been used"));

        CompanyContext.setCompanyId(companyId);

        TokenIssuer.RotationResult rotation = tokenIssuer.rotate(rawRefreshToken, ipAddress, userAgent);
        RefreshToken presented = rotation.storedToken();

        User user = userRepository.findByIdWithinCompany(rotation.userId(), companyId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or has already been used"));

        if (user.isDisabled()) {
            // A refresh must not resurrect an account that was disabled mid-session.
            sessionService.revokeAllSessions(user.getId(), "ACCOUNT_DISABLED");
            throw new ForbiddenException(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled.");
        }

        UserSession session = sessionService.findActive(presented.getSessionId(), user.getId())
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.SESSION_EXPIRED, "Session has expired. Sign in again."));

        sessionService.touch(session);

        Duration refreshTtl = session.isRememberMe()
                ? properties.getRememberMeDuration()
                : jwtTokenProvider.refreshTokenTtl();

        TokenIssuer.TokenPair tokens = tokenIssuer.completeRotation(
                user, session, presented, refreshTtl, ipAddress, userAgent);

        return new AuthResult(user, tokens, session, companyBrand(companyId));
    }

    /** Display name and logo for the session's brand — best-effort, null when unresolvable. */
    private CompanyDirectoryPort.CompanyRef companyBrand(UUID companyId) {
        return companyDirectory.findById(companyId).orElse(null);
    }

    // ------------------------------------------------------------------ logout

    /**
     * Ends the current session, or every session when {@code allDevices} is set.
     *
     * <p>The presented access token is denylisted so it stops working immediately
     * rather than remaining valid for the rest of its 15 minutes.
     */
    @Transactional
    public void logout(String rawRefreshToken, boolean allDevices) {
        AuthenticatedUser principal = SecurityUtils.requireCurrentUser();

        tokenRevocationService.revokeCurrentAccessToken(principal);

        if (allDevices) {
            int sessions = sessionService.revokeAllSessions(
                    principal.userId(), RefreshToken.RevokeReason.LOGOUT_ALL);
            auditService.record(AuditAction.LOGOUT_ALL_DEVICES, "User", principal.userId(),
                    Map.of("sessionsRevoked", sessions));
            return;
        }

        // Without the refresh token we can still kill the access token, but the
        // session itself can only be identified via the refresh token it issued.
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                    .filter(token -> token.getUserId().equals(principal.userId()))
                    .ifPresent(token -> sessionService
                            .findActive(token.getSessionId(), principal.userId())
                            .ifPresent(session -> sessionService.revokeSession(
                                    session.getId(), "UserSession", RefreshToken.RevokeReason.LOGOUT)));
        }

        auditService.record(AuditAction.LOGOUT, "User", principal.userId(),
                Map.of("allDevices", false));
    }

    // ---------------------------------------------------------------------- me

    /**
     * Current user, read from the database rather than from token claims.
     *
     * <p>Claims are a 15-minute-old snapshot; reading through means a role change or
     * a disabled account is reflected on the next call instead of at the next refresh.
     */
    @Transactional(readOnly = true)
    public User currentUser() {
        AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
        return userRepository.findByIdWithinCompany(principal.userId(), principal.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()));
    }

    // ---------------------------------------------------------------- commands

    /** Everything the login use case needs, assembled by the controller. */
    public record LoginCommand(UUID companyId,
                               String companyCode,
                               String email,
                               String password,
                               boolean rememberMe,
                               String ipAddress,
                               String userAgent,
                               SessionService.DeviceInfo device) {
    }

    /**
     * Outcome of a successful login or refresh.
     *
     * @param company display name/logo of the signed-in company, for session branding;
     *                null in the unexpected case the company row cannot be resolved
     */
    public record AuthResult(User user, TokenIssuer.TokenPair tokens, UserSession session,
                             CompanyDirectoryPort.CompanyRef company) {
    }
}
