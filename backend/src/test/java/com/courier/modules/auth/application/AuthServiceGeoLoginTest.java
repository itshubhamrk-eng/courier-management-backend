package com.courier.modules.auth.application;

import com.courier.modules.auth.application.TokenRevocationService.RevocableTokenStore;
import com.courier.modules.auth.application.port.BranchDirectoryPort;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.auth.infrastructure.security.AuthUserDetails;
import com.courier.modules.security.application.GeoCountryService;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.UserBanService;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Foreign-country login rules. The incident and ban services are real (over mocked
 * repositories) so the tests assert the user's actual status, not just a call.
 */
class AuthServiceGeoLoginTest {

    private static final String FOREIGN_IP = "8.8.8.8";
    private static final String HOME_IP = "49.36.10.10";
    private static final String PRIVATE_IP = "10.0.0.1";
    private static final String EMAIL = "ops@acme.test";
    private static final String RIGHT = "right-password";
    private static final String WRONG = "wrong-password";
    private static final String GEO_MESSAGE = "Sign-in is not available from your location.";

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CompanyDirectoryPort companyDirectory = mock(CompanyDirectoryPort.class);
    private final UserPermissionsPort userPermissionsPort = mock(UserPermissionsPort.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SecurityIncidentRepository incidentRepository = mock(SecurityIncidentRepository.class);

    private final SecurityProperties securityProperties = new SecurityProperties();
    private final AuthProperties properties = new AuthProperties();

    private AuthService authService;
    private UUID companyId;
    private UUID userId;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        securityProperties.getGeo().setEnabled(true);
        securityProperties.getGeo().setAllowedCountry("IN");
        GeoCountryService geo = new GeoCountryService(securityProperties, address ->
                Optional.of(address.getHostAddress().equals(FOREIGN_IP) ? "US" : "IN"));

        ObjectProvider<RevocableTokenStore> noStore = mock(ObjectProvider.class);
        UserBanService banService = new UserBanService(
                userRepository, sessionService, noStore, jwtTokenProvider, auditService);
        SecurityIncidentService incidents = new SecurityIncidentService(
                incidentRepository, userRepository, banService, auditService, new ObjectMapper());

        authService = new AuthService(
                authenticationManager, userRepository, mock(RefreshTokenRepository.class), companyDirectory,
                mock(BranchDirectoryPort.class), userPermissionsPort, sessionService, tokenIssuer,
                loginAttemptService, mock(TokenRevocationService.class), jwtTokenProvider, properties,
                auditService, passwordEncoder, geo, incidents);

        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        user = User.builder()
                .email(EMAIL)
                .passwordHash("$2a$12$hash")
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.OPERATOR))
                .emailVerified(true)
                .build();
        user.setId(userId);
        user.setCompanyId(companyId);

        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, null, true, null, null)));
        when(userRepository.findByEmailWithinCompany(EMAIL, companyId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RIGHT, "$2a$12$hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$dummy");
        when(incidentRepository.save(any(SecurityIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(jwtTokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(userPermissionsPort.resolveEffectivePermissions(any(), any())).thenReturn(Set.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private AuthService.LoginCommand command(String email, String password, String ip) {
        return new AuthService.LoginCommand(companyId, null, email, password, false, ip, "Mozilla/5.0 JUnit",
                new SessionService.DeviceInfo("dev-1", "JUnit", "API_CLIENT", ip, "Mozilla/5.0 JUnit"));
    }

    private ForbiddenException loginFails(AuthService.LoginCommand command) {
        ForbiddenException e = catchThrowableOfType(() -> authService.login(command), ForbiddenException.class);
        assertThat(e).as("expected a ForbiddenException").isNotNull();
        return e;
    }

    private SecurityIncident savedIncident() {
        ArgumentCaptor<SecurityIncident> captor = ArgumentCaptor.forClass(SecurityIncident.class);
        verify(incidentRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertNoSessionOrTokens() {
        verify(authenticationManager, never()).authenticate(any());
        verify(sessionService, never()).openSession(any(), any(), anyBoolean(), any());
        verify(tokenIssuer, never()).issueForNewSession(any(), any(), any(), any(), any());
        verifyNoInteractions(loginAttemptService);
    }

    private void stubSuccessfulAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                AuthUserDetails.of(user, properties), null, java.util.List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        UserSession session = UserSession.builder()
                .userId(userId).lastSeenAt(Instant.now()).expiresAt(Instant.now().plus(Duration.ofDays(7))).build();
        session.setId(UUID.randomUUID());
        session.setCompanyId(companyId);
        when(sessionService.openSession(eq(userId), any(), anyBoolean(), any())).thenReturn(session);
        when(tokenIssuer.issueForNewSession(any(), any(), any(), any(), any()))
                .thenReturn(new TokenIssuer.TokenPair("access", "refresh",
                        Duration.ofMinutes(15), Duration.ofDays(7), session.getId()));
    }

    @Test
    @DisplayName("foreign IP + correct password: GEO_RESTRICTED, user banned, FOREIGN_LOGIN_CORRECT_PASSWORD, no tokens")
    void foreignCorrectPasswordBans() {
        ForbiddenException e = loginFails(command(EMAIL, RIGHT, FOREIGN_IP));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GEO_RESTRICTED);
        assertThat(e.getMessage()).isEqualTo(GEO_MESSAGE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        SecurityIncident incident = savedIncident();
        assertThat(incident.getRule()).isEqualTo(IncidentRule.FOREIGN_LOGIN_CORRECT_PASSWORD);
        assertThat(incident.getUserId()).isEqualTo(userId);
        assertThat(incident.getCompanyId()).isEqualTo(companyId);
        assertThat(incident.getEvidence())
                .contains("\"ip\":\"8.8.8.8\"", "\"country\":\"US\"",
                        "\"userAgent\":\"Mozilla/5.0 JUnit\"", "\"attemptedEmail\":\"ops@acme.test\"");
        verify(sessionService).revokeAllSessions(eq(userId), anyString());
        assertNoSessionOrTokens();
    }

    @Test
    @DisplayName("foreign IP + wrong password: same exception, user not banned, FOREIGN_LOGIN_ATTEMPT, counter untouched")
    void foreignWrongPasswordFlagsOnly() {
        ForbiddenException wrong = loginFails(command(EMAIL, WRONG, FOREIGN_IP));

        assertThat(wrong.getErrorCode()).isEqualTo(ErrorCode.GEO_RESTRICTED);
        assertThat(wrong.getMessage()).isEqualTo(GEO_MESSAGE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedAttempts()).isZero();
        SecurityIncident incident = savedIncident();
        assertThat(incident.getRule()).isEqualTo(IncidentRule.FOREIGN_LOGIN_ATTEMPT);
        assertThat(incident.getUserId()).isEqualTo(userId);
        verify(passwordEncoder).matches(WRONG, "$2a$12$hash");
        verify(sessionService, never()).revokeAllSessions(any(), anyString());
        assertNoSessionOrTokens();
    }

    @Test
    @DisplayName("correct and wrong password from abroad are indistinguishable to the client")
    void foreignResponsesIdentical() {
        ForbiddenException wrong = loginFails(command(EMAIL, WRONG, FOREIGN_IP));
        ForbiddenException right = loginFails(command(EMAIL, RIGHT, FOREIGN_IP));

        assertThat(right.getErrorCode()).isEqualTo(wrong.getErrorCode());
        assertThat(right.getMessage()).isEqualTo(wrong.getMessage());
    }

    @Test
    @DisplayName("foreign IP + unknown email: GEO_RESTRICTED, no incident, dummy hash still checked")
    void foreignUnknownEmailNoIncident() {
        when(userRepository.findByEmailWithinCompany("ghost@acme.test", companyId)).thenReturn(Optional.empty());

        ForbiddenException e = loginFails(command("ghost@acme.test", RIGHT, FOREIGN_IP));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GEO_RESTRICTED);
        assertThat(e.getMessage()).isEqualTo(GEO_MESSAGE);
        verify(incidentRepository, never()).save(any());
        verify(passwordEncoder).matches(eq(RIGHT), anyString());
        assertNoSessionOrTokens();
    }

    @Test
    @DisplayName("foreign IP + unknown company: still GEO_RESTRICTED, not COMPANY_INACTIVE")
    void foreignUnknownCompanyStillGeoRestricted() {
        UUID unknown = UUID.randomUUID();
        when(companyDirectory.findById(unknown)).thenReturn(Optional.empty());

        ForbiddenException e = loginFails(new AuthService.LoginCommand(
                unknown, null, EMAIL, RIGHT, false, FOREIGN_IP, "JUnit", null));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GEO_RESTRICTED);
        assertThat(e.getMessage()).isEqualTo(GEO_MESSAGE);
        verify(incidentRepository, never()).save(any());
        assertNoSessionOrTokens();
    }

    @Test
    @DisplayName("a failing incident raise is swallowed: the client still gets the same GEO_RESTRICTED")
    void raiseFailureSwallowed() {
        when(incidentRepository.save(any(SecurityIncident.class))).thenThrow(new IllegalStateException("db down"));

        ForbiddenException e = loginFails(command(EMAIL, RIGHT, FOREIGN_IP));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GEO_RESTRICTED);
        assertThat(e.getMessage()).isEqualTo(GEO_MESSAGE);
        assertNoSessionOrTokens();
    }

    @Test
    @DisplayName("ALLOWED country follows the normal login flow")
    void allowedCountryLogsIn() {
        stubSuccessfulAuthentication();

        AuthService.AuthResult result = authService.login(command(EMAIL, RIGHT, HOME_IP));

        assertThat(result.tokens().accessToken()).isEqualTo("access");
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UNKNOWN verdict (private IP) follows the normal login flow")
    void unknownVerdictLogsIn() {
        stubSuccessfulAuthentication();

        AuthService.AuthResult result = authService.login(command(EMAIL, RIGHT, PRIVATE_IP));

        assertThat(result.tokens().accessToken()).isEqualTo("access");
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("geo disabled: a foreign IP logs in normally")
    void geoDisabledUnchanged() {
        securityProperties.getGeo().setEnabled(false);
        stubSuccessfulAuthentication();

        AuthService.AuthResult result = authService.login(command(EMAIL, RIGHT, FOREIGN_IP));

        assertThat(result.tokens().accessToken()).isEqualTo("access");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(incidentRepository, never()).save(any());
    }
}
