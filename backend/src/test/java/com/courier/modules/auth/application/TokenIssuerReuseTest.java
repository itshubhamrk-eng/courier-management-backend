package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.application.port.UserCompanyRolesPort;
import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.UserBanService;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh-token reuse detection feeding the security-incident module.
 *
 * <p>Uses the real {@link SecurityIncidentService} (repositories and the ban service mocked)
 * so "user banned" and "incident saved" are asserted on real state, and a recording
 * transaction manager so the test fails if the incident is raised while the family-revoke
 * transaction is still open (that ordering deadlocks on InnoDB in production: the raise runs
 * REQUIRES_NEW and would wait on the outer transaction's refresh_tokens row locks).
 */
class TokenIssuerReuseTest {

    private static final String RAW_TOKEN = "raw-refresh-token-value";
    private static final String ORIGINAL_IP = "10.0.0.1";

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final SecurityIncidentRepository incidentRepository = mock(SecurityIncidentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserBanService userBanService = mock(UserBanService.class);
    private final Claims claims = mock(Claims.class);

    private final RecordingTxManager txManager = new RecordingTxManager();
    private SecurityIncidentService incidentService;
    private TokenIssuer tokenIssuer;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private User user;

    /** Snapshots of the transaction state at the moment each side effect happened. */
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        incidentService = new SecurityIncidentService(
                incidentRepository, userRepository, userBanService, auditService, new ObjectMapper());
        tokenIssuer = new TokenIssuer(jwtTokenProvider, refreshTokenRepository, auditService,
                mock(CompanyDirectoryPort.class), mock(UserPermissionsPort.class),
                mock(UserCompanyRolesPort.class), incidentService, new SecurityProperties(), txManager);

        when(claims.getSubject()).thenReturn(userId.toString());
        when(jwtTokenProvider.parseRefreshToken(RAW_TOKEN)).thenReturn(Optional.of(claims));

        user = User.builder().email("victim@x.com").status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.OPERATOR)).build();
        user.setId(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(incidentRepository.save(any(SecurityIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        when(refreshTokenRepository.revokeFamily(eq(familyId), anyString(), any())).thenAnswer(inv -> {
            events.add("revokeFamily activeTx=" + txManager.active
                    + " propagation=" + txManager.lastPropagation);
            return 3;
        });
        doAnswer(inv -> {
            events.add("ban activeTx=" + txManager.active + " commits=" + txManager.commits);
            user.setStatus(UserStatus.DISABLED);
            return null;
        }).when(userBanService).ban(eq(userId), anyString());
    }

    private RefreshToken presented(Duration revokedAgo, String replacedByJti) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .sessionId(sessionId)
                .familyId(familyId)
                .jti("jti-1")
                .tokenHash(TokenHasher.hash(RAW_TOKEN))
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .ipAddress(ORIGINAL_IP)
                .userAgent("Original-UA")
                .revokedAt(Instant.now().minus(revokedAgo))
                .revokedReason(replacedByJti != null ? RefreshToken.RevokeReason.ROTATED
                        : RefreshToken.RevokeReason.LOGOUT)
                .replacedByJti(replacedByJti)
                .build();
        token.setId(UUID.randomUUID());
        token.setCompanyId(companyId);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN))).thenReturn(Optional.of(token));
        return token;
    }

    private void assertInvalidRefresh(String ip) {
        assertThatThrownBy(() -> tokenIssuer.rotate(RAW_TOKEN, ip, "Replay-UA"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private SecurityIncident savedIncident() {
        ArgumentCaptor<SecurityIncident> captor = ArgumentCaptor.forClass(SecurityIncident.class);
        verify(incidentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void a_rotatedTokenReusedAfter60sFromDifferentIp_bansUserAndStillRejects() {
        presented(Duration.ofSeconds(60), "jti-2");

        assertInvalidRefresh("203.0.113.9");

        SecurityIncident incident = savedIncident();
        assertThat(incident.getRule()).isEqualTo(IncidentRule.REFRESH_TOKEN_REUSE);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.BANNED);
        assertThat(incident.getCompanyId()).isEqualTo(companyId);
        assertThat(incident.getUserId()).isEqualTo(userId);
        assertThat(incident.getEvidence())
                .contains("\"familyId\":\"" + familyId + "\"")
                .contains("\"sessionId\":\"" + sessionId + "\"")
                .contains("\"presentedFromIp\":\"203.0.113.9\"")
                .contains("\"originalIp\":\"" + ORIGINAL_IP + "\"")
                .contains("\"userAgent\":\"Replay-UA\"")
                .contains("\"revokedAt\":");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(refreshTokenRepository).revokeFamily(
                eq(familyId), eq(RefreshToken.RevokeReason.REUSE_DETECTED), any());
    }

    @Test
    void a_familyRevokeCommitsInItsOwnTransactionBeforeTheIncidentIsRaised() {
        presented(Duration.ofSeconds(60), "jti-2");

        assertInvalidRefresh("203.0.113.9");

        // The family revoke runs inside a REQUIRES_NEW transaction of its own, and the ban
        // only happens once that transaction has committed and nothing of ours is open.
        // If raise ran while the revoke writes were uncommitted, the ban line would show
        // activeTx=1/commits=0 (or the revoke would run with no transaction of its own and be
        // rolled back with the outer one when INVALID_REFRESH_TOKEN is thrown).
        assertThat(events).containsExactly(
                "revokeFamily activeTx=1 propagation=" + TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                "ban activeTx=0 commits=1");
        assertThat(txManager.rollbacks).isZero();
    }

    @Test
    void b_reusedAfter3sSameSessionAndIp_isConcurrentRefresh_noIncidentNoBanFamilyIntact() {
        presented(Duration.ofSeconds(3), "jti-2");

        assertInvalidRefresh(ORIGINAL_IP);

        verify(refreshTokenRepository, never()).revokeFamily(any(), anyString(), any());
        verify(incidentRepository, never()).save(any());
        verify(userBanService, never()).ban(any(), anyString());
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(txManager.begun).isZero();
    }

    @Test
    void c_reusedAfter3sFromDifferentIp_raisesIncident() {
        presented(Duration.ofSeconds(3), "jti-2");

        assertInvalidRefresh("203.0.113.9");

        assertThat(savedIncident().getRule()).isEqualTo(IncidentRule.REFRESH_TOKEN_REUSE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(refreshTokenRepository).revokeFamily(
                eq(familyId), eq(RefreshToken.RevokeReason.REUSE_DETECTED), any());
    }

    @Test
    void d_revokedByLogout_rejectsWithoutIncident() {
        presented(Duration.ofSeconds(60), null);

        assertInvalidRefresh("203.0.113.9");

        verify(incidentRepository, never()).save(any());
        verify(userBanService, never()).ban(any(), anyString());
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // Today's behaviour is kept: the family is still destroyed.
        verify(refreshTokenRepository).revokeFamily(
                eq(familyId), eq(RefreshToken.RevokeReason.REUSE_DETECTED), any());
    }

    @Test
    void raiseFailureNeverChangesTheClientError() {
        presented(Duration.ofSeconds(60), "jti-2");
        when(userRepository.findByIdForUpdate(userId)).thenThrow(new IllegalStateException("db down"));

        assertInvalidRefresh("203.0.113.9");

        verify(refreshTokenRepository).revokeFamily(
                eq(familyId), eq(RefreshToken.RevokeReason.REUSE_DETECTED), any());
        assertThat(txManager.commits).isEqualTo(1);
    }

    /** Tracks begin/commit/rollback so the test can see which transaction a side effect ran in. */
    static final class RecordingTxManager implements PlatformTransactionManager {
        int begun;
        int active;
        int commits;
        int rollbacks;
        int lastPropagation = -1;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begun++;
            active++;
            lastPropagation = definition == null
                    ? TransactionDefinition.PROPAGATION_REQUIRED : definition.getPropagationBehavior();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            active--;
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            active--;
            rollbacks++;
        }
    }
}
