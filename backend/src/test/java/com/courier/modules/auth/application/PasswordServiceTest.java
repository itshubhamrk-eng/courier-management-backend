package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.NotificationPort;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.PasswordResetToken;
import com.courier.modules.auth.domain.PasswordResetTokenRepository;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private CompanyDirectoryPort companyDirectory;
    @Mock private SessionService sessionService;
    @Mock private NotificationPort notificationPort;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private AuditService auditService;

    private PasswordService passwordService;
    private PasswordEncoder passwordEncoder;
    private AuthProperties properties;

    private UUID companyId;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        PasswordPolicy policy = new PasswordPolicy(properties);
        policy.loadCommonPasswords();
        // Cost 4 rather than 12: this is a unit test, not a benchmark of BCrypt.
        passwordEncoder = new BCryptPasswordEncoder(4);

        passwordService = new PasswordService(
                userRepository, resetTokenRepository, companyDirectory, policy, passwordEncoder,
                sessionService, notificationPort, tokenRevocationService, properties, auditService);

        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
                .email("ops@acme.test")
                .passwordHash(passwordEncoder.encode("Original-Passw0rd"))
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        user.setId(userId);
        user.setCompanyId(companyId);

        when(companyDirectory.findById(companyId))
                .thenReturn(Optional.of(new CompanyDirectoryPort.CompanyRef(companyId, null, true, null, null)));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    // ------------------------------------------------------------ forgot

    @Test
    @DisplayName("an unknown email completes silently and issues no token")
    void forgotUnknownEmailIsSilent() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Must not throw: the caller cannot be allowed to distinguish this case.
        passwordService.forgotPassword(companyId, "nobody@acme.test", "10.0.0.1");

        verify(resetTokenRepository, never()).save(any());
        verify(notificationPort, never()).sendPasswordResetLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an unknown company completes silently too")
    void forgotUnknownCompanyIsSilent() {
        UUID unknown = UUID.randomUUID();
        when(companyDirectory.findById(unknown)).thenReturn(Optional.empty());

        passwordService.forgotPassword(unknown, "ops@acme.test", "10.0.0.1");

        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("a known email issues exactly one token and sends the link")
    void forgotKnownEmailIssuesToken() {
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));

        passwordService.forgotPassword(companyId, "ops@acme.test", "10.0.0.1");

        // Outstanding links are invalidated so only the newest one works.
        verify(resetTokenRepository).consumeAllForUser(eq(userId), any());
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(notificationPort).sendPasswordResetLink(eq("ops@acme.test"), anyString(), anyString());
    }

    @Test
    @DisplayName("only the hash of the reset token is persisted")
    void resetTokenStoredHashedOnly() {
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));

        passwordService.forgotPassword(companyId, "ops@acme.test", "10.0.0.1");

        org.mockito.ArgumentCaptor<PasswordResetToken> saved =
                org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        org.mockito.ArgumentCaptor<String> link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resetTokenRepository).save(saved.capture());
        verify(notificationPort).sendPasswordResetLink(anyString(), anyString(), link.capture());

        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.hash(rawToken));
    }

    @Test
    @DisplayName("a disabled account gets no reset link")
    void forgotSuppressedForDisabledUser() {
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmail("ops@acme.test")).thenReturn(Optional.of(user));

        passwordService.forgotPassword(companyId, "ops@acme.test", "10.0.0.1");

        verify(resetTokenRepository, never()).save(any());
    }

    // ------------------------------------------------------------- reset

    private PasswordResetToken usableToken(String rawToken) {
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.hash(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        token.setId(UUID.randomUUID());
        token.setCompanyId(companyId);
        return token;
    }

    @Test
    @DisplayName("a valid reset changes the password, consumes the token and kills all sessions")
    void resetSucceeds() {
        String raw = "reset-token-abc";
        PasswordResetToken token = usableToken(raw);
        when(resetTokenRepository.findByTokenHash(TokenHasher.hash(raw))).thenReturn(Optional.of(token));
        when(userRepository.findByIdWithinCompany(userId, companyId)).thenReturn(Optional.of(user));

        passwordService.resetPassword(raw, "Brand-New-Passw0rd", "10.0.0.1");

        assertThat(passwordEncoder.matches("Brand-New-Passw0rd", user.getPasswordHash())).isTrue();
        assertThat(token.isConsumed()).isTrue();
        // A reset is the standard response to a compromise; the attacker's sessions
        // must not survive it.
        verify(sessionService).revokeAllSessions(userId, RefreshToken.RevokeReason.PASSWORD_RESET);
    }

    @Test
    @DisplayName("a reset clears an account lock — this is the unlock path")
    void resetUnlocksAccount() {
        user.setFailedAttempts(5);
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));

        String raw = "reset-token-abc";
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usableToken(raw)));
        when(userRepository.findByIdWithinCompany(userId, companyId)).thenReturn(Optional.of(user));

        passwordService.resetPassword(raw, "Brand-New-Passw0rd", "10.0.0.1");

        assertThat(user.isTemporarilyLocked()).isFalse();
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("a consumed token cannot be replayed")
    void consumedTokenRejected() {
        String raw = "reset-token-abc";
        PasswordResetToken token = usableToken(raw);
        token.consume();
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(raw, "Brand-New-Passw0rd", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenRejected() {
        String raw = "reset-token-abc";
        PasswordResetToken token = usableToken(raw);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(raw, "Brand-New-Passw0rd", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("unknown and expired tokens produce the same message")
    void resetRejectionsAreUniform() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        String unknown = catchMessage(() -> passwordService.resetPassword("x", "Brand-New-Passw0rd", null));

        PasswordResetToken expired = usableToken("y");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        String expiredMessage = catchMessage(() -> passwordService.resetPassword("y", "Brand-New-Passw0rd", null));

        assertThat(unknown).isEqualTo(expiredMessage);
    }

    @Test
    @DisplayName("the password policy applies to a reset")
    void resetEnforcesPolicy() {
        String raw = "reset-token-abc";
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usableToken(raw)));
        when(userRepository.findByIdWithinCompany(userId, companyId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordService.resetPassword(raw, "password123", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too common");
    }

    @Test
    @DisplayName("the company is bound from the token row, since the caller is anonymous")
    void resetBindsCompanyFromToken() {
        String raw = "reset-token-abc";
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usableToken(raw)));
        when(userRepository.findByIdWithinCompany(userId, companyId)).thenReturn(Optional.of(user));

        assertThat(CompanyContext.isSet()).isFalse();
        passwordService.resetPassword(raw, "Brand-New-Passw0rd", "10.0.0.1");

        assertThat(CompanyContext.getCompanyId()).contains(companyId);
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected BusinessRuleException");
        } catch (BusinessRuleException e) {
            return e.getMessage();
        }
    }
}
