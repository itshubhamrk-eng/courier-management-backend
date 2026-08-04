package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh-token rotation and replay detection — the security core of the module.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenIssuerTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditService auditService;
    @Mock private CompanyDirectoryPort companyDirectory;
    @Mock private Claims claims;

    private TokenIssuer tokenIssuer;

    private UUID userId;
    private UUID familyId;
    private static final String RAW_TOKEN = "raw-refresh-token-value";

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer(jwtTokenProvider, refreshTokenRepository, auditService, companyDirectory);
        userId = UUID.randomUUID();
        familyId = UUID.randomUUID();

        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.getId()).thenReturn("jti-1");
        when(jwtTokenProvider.parseRefreshToken(RAW_TOKEN)).thenReturn(Optional.of(claims));
    }

    private RefreshToken storedToken() {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .sessionId(UUID.randomUUID())
                .familyId(familyId)
                .jti("jti-1")
                .tokenHash(TokenHasher.hash(RAW_TOKEN))
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        token.setId(UUID.randomUUID());
        return token;
    }

    @Test
    @DisplayName("a valid, unused token rotates successfully")
    void validTokenRotates() {
        RefreshToken stored = storedToken();
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(stored));

        TokenIssuer.RotationResult result = tokenIssuer.rotate(RAW_TOKEN, "10.0.0.1", "JUnit");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.storedToken()).isSameAs(stored);
        verify(refreshTokenRepository, never()).revokeFamily(any(), anyString(), any());
    }

    @Test
    @DisplayName("replaying a revoked token destroys the entire rotation family")
    void replayRevokesFamily() {
        RefreshToken stored = storedToken();
        stored.revoke(RefreshToken.RevokeReason.ROTATED);   // already used
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeFamily(eq(familyId), anyString(), any())).thenReturn(3);

        assertThatThrownBy(() -> tokenIssuer.rotate(RAW_TOKEN, "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((UnauthorizedException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        // This is the whole point: one replay kills every token descended from that
        // login, so a stolen refresh token is usable at most once.
        verify(refreshTokenRepository).revokeFamily(
                eq(familyId), eq(RefreshToken.RevokeReason.REUSE_DETECTED), any());
    }

    @Test
    @DisplayName("a replay is audited as a security event, marked unsuccessful")
    void replayIsAudited() {
        RefreshToken stored = storedToken();
        stored.revoke(RefreshToken.RevokeReason.ROTATED);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> tokenIssuer.rotate(RAW_TOKEN, "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditService).record(
                eq(AuditAction.REFRESH_TOKEN_REUSE_DETECTED), eq("RefreshToken"),
                eq(stored.getId()), anyMap(), eq(false));
    }

    @Test
    @DisplayName("an expired token is rejected and marked revoked")
    void expiredTokenRejected() {
        RefreshToken stored = storedToken();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> tokenIssuer.rotate(RAW_TOKEN, "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
        // An expiry is not a replay, so the family survives.
        verify(refreshTokenRepository, never()).revokeFamily(any(), anyString(), any());
    }

    @Test
    @DisplayName("a token with no matching row is rejected")
    void unknownTokenRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenIssuer.rotate(RAW_TOKEN, "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("invalid or has already been used");
    }

    @Test
    @DisplayName("a token failing signature or type validation never reaches the database")
    void invalidSignatureRejectedEarly() {
        when(jwtTokenProvider.parseRefreshToken("forged")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenIssuer.rotate("forged", "10.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("every rejection returns the same opaque message")
    void rejectionsAreUniform() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        String unknown = catchMessage(() -> tokenIssuer.rotate(RAW_TOKEN, null, null));

        RefreshToken expired = storedToken();
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        String expiredMessage = catchMessage(() -> tokenIssuer.rotate(RAW_TOKEN, null, null));

        // A probe must not be able to tell "never existed" from "expired".
        assertThat(unknown).isEqualTo(expiredMessage);
    }

    @Test
    @DisplayName("the stored hash never equals the raw token")
    void tokenIsStoredHashed() {
        RefreshToken stored = storedToken();

        assertThat(stored.getTokenHash()).isNotEqualTo(RAW_TOKEN);
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(TokenHasher.matches(RAW_TOKEN, stored.getTokenHash())).isTrue();
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnauthorizedException");
        } catch (UnauthorizedException e) {
            return e.getMessage();
        }
    }
}
