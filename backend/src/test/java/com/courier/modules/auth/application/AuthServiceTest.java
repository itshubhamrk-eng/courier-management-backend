package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.BranchDirectoryPort;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.LoginFailureReason;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.auth.infrastructure.security.AuthUserDetails;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.JwtTokenProvider;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login, refresh and logout behaviour.
 *
 * <p>The tests that matter most here are the negative ones: that failures are
 * indistinguishable, that a disabled account cannot refresh its way back in, and
 * that the company is validated before anything else happens.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CompanyDirectoryPort companyDirectory;
    @Mock private BranchDirectoryPort branchDirectory;
    @Mock private SessionService sessionService;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditService auditService;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthProperties properties;
    private AuthService authService;

    private UUID companyId;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        authService = new AuthService(
                authenticationManager, userRepository, refreshTokenRepository, companyDirectory, branchDirectory,
                sessionService, tokenIssuer, loginAttemptService, emailVerificationService,
                tokenRevocationService, jwtTokenProvider, properties, auditService, passwordEncoder);

        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
                .email("ops@acme.test")
                .passwordHash("$2a$12$hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.OPERATOR))
                .emailVerified(true)
                .build();
        user.setId(userId);
        user.setCompanyId(companyId);

        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, null, true, null, null)));
        when(jwtTokenProvider.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(jwtTokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private AuthService.LoginCommand command(String password, boolean rememberMe) {
        return new AuthService.LoginCommand(
                companyId, null, "ops@acme.test", password, rememberMe, "10.0.0.1", "JUnit",
                new SessionService.DeviceInfo("dev-1", "JUnit", "API_CLIENT", "10.0.0.1", "JUnit"));
    }

    private void stubSuccessfulAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                AuthUserDetails.of(user, properties), null, java.util.List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        UserSession session = UserSession.builder()
                .userId(userId)
                .lastSeenAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        session.setId(UUID.randomUUID());
        session.setCompanyId(companyId);

        when(sessionService.openSession(eq(userId), any(), anyBoolean(), any())).thenReturn(session);
        when(tokenIssuer.issueForNewSession(any(), any(), any(), any(), any()))
                .thenReturn(new TokenIssuer.TokenPair("access", "refresh",
                        Duration.ofMinutes(15), Duration.ofDays(7), session.getId()));
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("a valid login returns a token pair and records the success")
    void loginSucceeds() {
        stubSuccessfulAuthentication();

        AuthService.AuthResult result = authService.login(command("correct-password", false));

        assertThat(result.user().getId()).isEqualTo(userId);
        assertThat(result.tokens().accessToken()).isEqualTo("access");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh");
        verify(loginAttemptService).recordSuccess(eq(userId), any(), eq("10.0.0.1"), eq("JUnit"));
    }

    @Test
    @DisplayName("a race on the login-bookkeeping update never turns an already-successful login into a failure")
    void loginSurvivesBookkeepingRace() {
        // Found running a k6 load test with more virtual users than distinct accounts:
        // two VUs logging into the same account within the same millisecond raced on
        // User's own optimistic lock (last-login timestamp / failed-attempt reset).
        stubSuccessfulAuthentication();
        org.mockito.Mockito.doThrow(new org.springframework.dao.OptimisticLockingFailureException("boom"))
                .when(loginAttemptService).recordSuccess(eq(userId), any(), eq("10.0.0.1"), eq("JUnit"));

        AuthService.AuthResult result = authService.login(command("correct-password", false));

        assertThat(result.tokens().accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("a platform-role user's login-bookkeeping company mismatch never turns an "
            + "already-successful login into a 500")
    void loginSurvivesBookkeepingCompanyIsolation() {
        // Found in prod 2026-09-02: SUPER_ADMIN signed in with a companyCode resolved from
        // the request (not their own platform company) — password verified and the session
        // was already issued, but the bookkeeping update on their User row then tripped
        // CompanyIsolationException, previously uncaught here and surfaced as an unhandled 500.
        stubSuccessfulAuthentication();
        org.mockito.Mockito.doThrow(new com.courier.shared.exception.CompanyIsolationException(
                        "Cross-company update blocked for User"))
                .when(loginAttemptService).recordSuccess(eq(userId), any(), eq("10.0.0.1"), eq("JUnit"));

        AuthService.AuthResult result = authService.login(command("correct-password", false));

        assertThat(result.tokens().accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("the company is bound before any user lookup, so the company filter applies")
    void bindsCompanyBeforeLookup() {
        stubSuccessfulAuthentication();

        authService.login(command("correct-password", false));

        // If this were not bound, AuthUserDetailsService would search globally —
        // the exact cross-company hole the design exists to prevent.
        assertThat(CompanyContext.getCompanyId()).contains(companyId);
    }

    @Test
    @DisplayName("an unknown company is rejected before credentials are even examined")
    void unknownCompanyRejected() {
        UUID unknown = UUID.randomUUID();
        when(companyDirectory.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthService.LoginCommand(
                unknown, null, "ops@acme.test", "pw", false, "10.0.0.1", "JUnit", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Unknown or inactive company");

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("an inactive company cannot authenticate")
    void inactiveCompanyRejected() {
        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, null, false, null, null)));

        assertThatThrownBy(() -> authService.login(command("pw", false)))
                .isInstanceOf(ForbiddenException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("a wrong password and an unknown email are indistinguishable")
    void badCredentialsAreUniform() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        // Case 1: the user exists.
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));
        UnauthorizedException existing = catchUnauthorized(() -> authService.login(command("wrong", false)));

        // Case 2: the user does not.
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.empty());
        UnauthorizedException missing = catchUnauthorized(() -> authService.login(command("wrong", false)));

        assertThat(existing.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(missing.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(existing.getMessage()).isEqualTo(missing.getMessage());
    }

    @Test
    @DisplayName("a failure against a known user advances the lock counter")
    void failureAgainstKnownUserCounts() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));

        catchUnauthorized(() -> authService.login(command("wrong", false)));

        ArgumentCaptor<LoginFailureReason> reason = ArgumentCaptor.forClass(LoginFailureReason.class);
        verify(loginAttemptService).recordFailure(any(), eq(userId), anyString(),
                reason.capture(), anyString(), anyString());
        assertThat(reason.getValue()).isEqualTo(LoginFailureReason.BAD_PASSWORD);
    }

    @Test
    @DisplayName("a failure against an unknown email is recorded without a user")
    void failureAgainstUnknownUserRecorded() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.empty());

        catchUnauthorized(() -> authService.login(command("wrong", false)));

        ArgumentCaptor<LoginFailureReason> reason = ArgumentCaptor.forClass(LoginFailureReason.class);
        verify(loginAttemptService).recordFailure(any(), eq(null), anyString(),
                reason.capture(), anyString(), anyString());
        assertThat(reason.getValue()).isEqualTo(LoginFailureReason.UNKNOWN_USER);
    }

    @Test
    @DisplayName("a locked account returns 423, not a credential error")
    void lockedAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(command("pw", false)))
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("a disabled account returns ACCOUNT_DISABLED")
    void disabledAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(command("pw", false)))
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    @DisplayName("an expired password returns PASSWORD_EXPIRED")
    void expiredPassword() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new CredentialsExpiredException("expired"));

        assertThatThrownBy(() -> authService.login(command("pw", false)))
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_EXPIRED);
    }

    @Test
    @DisplayName("an unverified email is rejected only after the password verifies")
    void unverifiedEmailRejectedAfterPasswordCheck() {
        user.setEmailVerified(false);
        stubSuccessfulAuthentication();

        assertThatThrownBy(() -> authService.login(command("correct-password", false)))
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        // A fresh link is sent, so a user who lost the first email can self-recover.
        verify(emailVerificationService).reissueIfDue(user);
        // No session is opened for an unverified account.
        verify(tokenIssuer, never()).issueForNewSession(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("remember me extends the refresh token to the configured duration")
    void rememberMeExtendsRefreshTtl() {
        stubSuccessfulAuthentication();

        authService.login(command("correct-password", true));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(sessionService).openSession(eq(userId), ttl.capture(), eq(true), any());
        assertThat(ttl.getValue()).isEqualTo(properties.getRememberMeDuration());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    @DisplayName("without remember me the standard refresh TTL is used")
    void standardTtlWithoutRememberMe() {
        stubSuccessfulAuthentication();

        authService.login(command("correct-password", false));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(sessionService).openSession(eq(userId), ttl.capture(), eq(false), any());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("the throttle is consulted before credentials are checked")
    void throttleCheckedFirst() {
        org.mockito.Mockito.doThrow(new BusinessRuleException(
                        ErrorCode.RATE_LIMIT_EXCEEDED, "Too many failed sign-in attempts."))
                .when(loginAttemptService).assertNotThrottled(anyString(), anyString());

        assertThatThrownBy(() -> authService.login(command("pw", false)))
                .isInstanceOf(BusinessRuleException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("an unknown company code is refused without revealing whether it exists")
    void unknownCompanyCodeRefused() {
        when(companyDirectory.findByCode("acme-couriers")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthService.LoginCommand(
                null, "acme-couriers", "ops@acme.test", "pw", false, "10.0.0.1", "JUnit", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Unknown or inactive company");
    }

    @Test
    @DisplayName("a suspended company is refused with the same message as an unknown one")
    void inactiveCompanyRefused() {
        when(companyDirectory.findByCode("acme-couriers")).thenReturn(Optional.of(
                new CompanyDirectoryPort.CompanyRef(companyId, "ACME_COURIERS", false, null, null)));

        assertThatThrownBy(() -> authService.login(new AuthService.LoginCommand(
                null, "acme-couriers", "ops@acme.test", "pw", false, "10.0.0.1", "JUnit", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Unknown or inactive company");
    }

    @Test
    @DisplayName("a login with neither companyId nor slug falls through to the platform lookup "
            + "and is indistinguishable from a wrong password")
    void companyRequired() {
        // Omitting the company code is how a SUPER_ADMIN / PLATFORM_ADMIN signs in: the
        // home company is derived server-side. An ordinary address like this one is not a
        // platform account, so the lookup finds nothing — and the refusal is deliberately
        // the same 401 a wrong password gets, so nothing about ordinary accounts leaks.
        // This test previously asserted a ForbiddenException "companyId is required"; that
        // was the behaviour before platform sign-in landed, and the assertion went stale
        // rather than the production path regressing.
        when(userRepository.findPlatformUsersByEmail(eq("ops@acme.test"), anySet()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> authService.login(new AuthService.LoginCommand(
                null, null, "ops@acme.test", "pw", false, "10.0.0.1", "JUnit", null)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // ---------------------------------------------------------------- refresh

    @Test
    @DisplayName("a malformed refresh token is rejected without touching the database")
    void refreshRejectsMalformedToken() {
        when(jwtTokenProvider.parseRefreshToken("garbage")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("garbage", "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(tokenIssuer, never()).rotate(anyString(), any(), any());
    }

    // ------------------------------------------------------------ impersonate

    private static final UUID PLATFORM_COMPANY = UUID.randomUUID();

    private User superAdmin(UUID id) {
        User u = User.builder()
                .email("super@platform.test")
                .passwordHash("$2a$12$super-hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.SUPER_ADMIN))
                .emailVerified(true)
                .build();
        u.setId(id);
        u.setCompanyId(PLATFORM_COMPANY);
        return u;
    }

    private void signedInAsSuperAdmin(UUID id) {
        AuthenticatedUser principal = new AuthenticatedUser(
                id, PLATFORM_COMPANY, "super@platform.test", Set.of("SUPER_ADMIN"), "jti");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    @Test
    @DisplayName("impersonation mints a token acting as the company's real COMPANY_ADMIN, not the super admin")
    void impersonateSucceeds() {
        UUID superAdminId = UUID.randomUUID();
        User admin = superAdmin(superAdminId);
        signedInAsSuperAdmin(superAdminId);
        when(userRepository.findByIdWithinCompany(superAdminId, PLATFORM_COMPANY)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getPasswordHash())).thenReturn(true);

        UUID targetCompanyId = UUID.randomUUID();
        when(companyDirectory.findById(targetCompanyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(
                        targetCompanyId, "TARGET", true, "Target Co", null)));

        User companyAdmin = User.builder()
                .email("admin@target.test")
                .passwordHash("$2a$12$irrelevant")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.COMPANY_ADMIN))
                .emailVerified(true)
                .build();
        UUID companyAdminId = UUID.randomUUID();
        companyAdmin.setId(companyAdminId);
        companyAdmin.setCompanyId(targetCompanyId);
        when(userRepository.findByCompanyIdAndRoleAndStatus(targetCompanyId, Role.COMPANY_ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(companyAdmin));

        when(jwtTokenProvider.generateImpersonationAccessToken(
                eq(companyAdminId), eq(targetCompanyId), eq("admin@target.test"), anySet(),
                any(), any(), eq("Target Co"), any(), eq(superAdminId), eq("super@platform.test"), any()))
                .thenReturn("impersonation-jwt");

        AuthService.ImpersonationResult result =
                authService.impersonateCompany(targetCompanyId, "correct-password", "10.0.0.1");

        assertThat(result.accessToken()).isEqualTo("impersonation-jwt");
        assertThat(result.impersonatedUser().getId()).isEqualTo(companyAdminId);
        assertThat(result.impersonator().getId()).isEqualTo(superAdminId);
        assertThat(CompanyContext.getCompanyId()).isEmpty(); // runAs restores the prior (unbound) context
        verify(auditService).record(eq(com.courier.shared.audit.domain.AuditAction.COMPANY_IMPERSONATED),
                eq("Company"), eq(targetCompanyId), any());
    }

    @Test
    @DisplayName("impersonation refuses a wrong step-up password without touching the target company")
    void impersonateRejectsWrongPassword() {
        UUID superAdminId = UUID.randomUUID();
        User admin = superAdmin(superAdminId);
        signedInAsSuperAdmin(superAdminId);
        when(userRepository.findByIdWithinCompany(superAdminId, PLATFORM_COMPANY)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", admin.getPasswordHash())).thenReturn(false);

        UUID targetCompanyId = UUID.randomUUID();

        assertThatThrownBy(() -> authService.impersonateCompany(targetCompanyId, "wrong-password", "10.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(companyDirectory, never()).findById(any());
        verify(jwtTokenProvider, never()).generateImpersonationAccessToken(
                any(), any(), any(), anySet(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("impersonation refuses a company with no active admin user rather than minting a token for nobody")
    void impersonateRejectsCompanyWithNoAdmin() {
        UUID superAdminId = UUID.randomUUID();
        User admin = superAdmin(superAdminId);
        signedInAsSuperAdmin(superAdminId);
        when(userRepository.findByIdWithinCompany(superAdminId, PLATFORM_COMPANY)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct-password", admin.getPasswordHash())).thenReturn(true);

        UUID targetCompanyId = UUID.randomUUID();
        when(companyDirectory.findById(targetCompanyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(
                        targetCompanyId, "TARGET", true, "Target Co", null)));
        when(userRepository.findByCompanyIdAndRoleAndStatus(targetCompanyId, Role.COMPANY_ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> authService.impersonateCompany(targetCompanyId, "correct-password", "10.0.0.1"))
                .isInstanceOf(com.courier.shared.exception.ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------ impersonate branch

    private void signedInAsCompanyAdmin(UUID id, UUID companyId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                id, companyId, "admin@acme.test", Set.of("COMPANY_ADMIN"), "jti");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    @Test
    @DisplayName("branch impersonation mints a token acting as the branch's real BRANCH_MANAGER, no password needed")
    void impersonateBranchSucceeds() {
        UUID companyAdminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        User admin = User.builder()
                .email("admin@acme.test")
                .passwordHash("$2a$12$admin-hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.COMPANY_ADMIN))
                .emailVerified(true)
                .build();
        admin.setId(companyAdminId);
        admin.setCompanyId(companyId);
        signedInAsCompanyAdmin(companyAdminId, companyId);
        when(userRepository.findByIdWithinCompany(companyAdminId, companyId)).thenReturn(Optional.of(admin));
        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, "ACME", true, "Acme Co", null)));

        UUID targetBranchId = UUID.randomUUID();
        when(branchDirectory.findById(targetBranchId, companyId))
                .thenReturn(Optional.of(new BranchDirectoryPort.BranchRef(targetBranchId, "PUNE", "Pune Branch", true)));

        User branchManager = User.builder()
                .email("manager@acme.test")
                .passwordHash("$2a$12$irrelevant")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.BRANCH_MANAGER))
                .emailVerified(true)
                .build();
        UUID managerId = UUID.randomUUID();
        branchManager.setId(managerId);
        branchManager.setCompanyId(companyId);
        when(userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(
                companyId, targetBranchId, Role.BRANCH_MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(branchManager));

        when(jwtTokenProvider.generateImpersonationAccessToken(
                eq(managerId), eq(companyId), eq("manager@acme.test"), anySet(),
                any(), any(), eq("Acme Co"), any(), eq(companyAdminId), eq("admin@acme.test"), any()))
                .thenReturn("impersonation-jwt");

        AuthService.ImpersonationResult result = authService.impersonateBranch(targetBranchId, "10.0.0.1");

        assertThat(result.accessToken()).isEqualTo("impersonation-jwt");
        assertThat(result.impersonatedUser().getId()).isEqualTo(managerId);
        assertThat(result.impersonator().getId()).isEqualTo(companyAdminId);
        verify(auditService).record(eq(com.courier.shared.audit.domain.AuditAction.BRANCH_IMPERSONATED),
                eq("Branch"), eq(targetBranchId), any());
    }

    @Test
    @DisplayName("branch impersonation refuses an unknown or foreign branch")
    void impersonateBranchRejectsUnknownBranch() {
        UUID companyAdminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        User admin = User.builder()
                .email("admin@acme.test")
                .passwordHash("$2a$12$admin-hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.COMPANY_ADMIN))
                .emailVerified(true)
                .build();
        admin.setId(companyAdminId);
        admin.setCompanyId(companyId);
        signedInAsCompanyAdmin(companyAdminId, companyId);
        when(userRepository.findByIdWithinCompany(companyAdminId, companyId)).thenReturn(Optional.of(admin));

        UUID targetBranchId = UUID.randomUUID();
        when(branchDirectory.findById(targetBranchId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.impersonateBranch(targetBranchId, "10.0.0.1"))
                .isInstanceOf(com.courier.shared.exception.ResourceNotFoundException.class);

        verify(jwtTokenProvider, never()).generateImpersonationAccessToken(
                any(), any(), any(), anySet(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("branch impersonation refuses a branch with no active manager rather than minting a token for nobody")
    void impersonateBranchRejectsBranchWithNoManager() {
        UUID companyAdminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        User admin = User.builder()
                .email("admin@acme.test")
                .passwordHash("$2a$12$admin-hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.COMPANY_ADMIN))
                .emailVerified(true)
                .build();
        admin.setId(companyAdminId);
        admin.setCompanyId(companyId);
        signedInAsCompanyAdmin(companyAdminId, companyId);
        when(userRepository.findByIdWithinCompany(companyAdminId, companyId)).thenReturn(Optional.of(admin));

        UUID targetBranchId = UUID.randomUUID();
        when(branchDirectory.findById(targetBranchId, companyId))
                .thenReturn(Optional.of(new BranchDirectoryPort.BranchRef(targetBranchId, "PUNE", "Pune Branch", true)));
        when(userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(
                companyId, targetBranchId, Role.BRANCH_MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> authService.impersonateBranch(targetBranchId, "10.0.0.1"))
                .isInstanceOf(com.courier.shared.exception.ResourceNotFoundException.class);
    }

    private UnauthorizedException catchUnauthorized(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnauthorizedException");
        } catch (UnauthorizedException e) {
            return e;
        }
    }
}
