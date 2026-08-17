package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.LoginFailureReason;
import com.courier.modules.auth.domain.LoginHistory;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private LoginAttemptService service;
    private AuthProperties properties;
    private User user;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        service = new LoginAttemptService(loginHistoryRepository, userRepository, properties, auditService);

        companyId = UUID.randomUUID();
        user = User.builder()
                .email("ops@acme.test")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(UUID.randomUUID());
        user.setCompanyId(companyId);
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
    }

    @Test
    @DisplayName("allows a login while under the throttle limit")
    void underLimitAllowed() {
        when(loginHistoryRepository.countRecentFailures(anyString(), anyString(), any())).thenReturn(9L);

        assertThatCode(() -> service.assertNotThrottled("ops@acme.test", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuses once the email+IP failure limit is reached")
    void atLimitThrottled() {
        when(loginHistoryRepository.countRecentFailures(anyString(), anyString(), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.assertNotThrottled("ops@acme.test", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("the throttle is keyed by IP as well, so it cannot be used to lock out a victim")
    void throttleIsPerEmailAndIp() {
        service.assertNotThrottled("ops@acme.test", "10.0.0.1");

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(loginHistoryRepository).countRecentFailures(email.capture(), ip.capture(), any());

        assertThat(email.getValue()).isEqualTo("ops@acme.test");
        assertThat(ip.getValue()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("a bad password advances the lock counter")
    void badPasswordIncrementsCounter() {
        service.recordFailure(companyId, user.getId(), "ops@acme.test",
                LoginFailureReason.BAD_PASSWORD, "10.0.0.1", "JUnit");

        assertThat(user.getFailedAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("the account locks and is audited at the threshold")
    void locksAtThreshold() {
        for (int i = 0; i < properties.getLockoutThreshold(); i++) {
            service.recordFailure(companyId, user.getId(), "ops@acme.test",
                    LoginFailureReason.BAD_PASSWORD, "10.0.0.1", "JUnit");
        }

        assertThat(user.isTemporarilyLocked()).isTrue();
        verify(auditService).record(eq(AuditAction.ACCOUNT_LOCKED), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    @DisplayName("an unknown email is recorded without a user and without a counter")
    void unknownUserRecordedWithoutCounter() {
        service.recordFailure(companyId, null, "nobody@acme.test",
                LoginFailureReason.UNKNOWN_USER, "10.0.0.1", "JUnit");

        ArgumentCaptor<LoginHistory> saved = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(saved.capture());

        assertThat(saved.getValue().getUserId()).isNull();
        assertThat(saved.getValue().getAttemptedEmail()).isEqualTo("nobody@acme.test");
        assertThat(saved.getValue().isSuccess()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a lock-reason failure does not itself advance the counter")
    void lockedReasonDoesNotDoubleCount() {
        // Otherwise a locked account would keep extending its own lock on every
        // attempt, turning a 15-minute lock into an indefinite one.
        service.recordFailure(companyId, user.getId(), "ops@acme.test",
                LoginFailureReason.ACCOUNT_LOCKED, "10.0.0.1", "JUnit");

        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("a success clears the counters and records history")
    void successClearsCounters() {
        user.setFailedAttempts(3);
        UUID sessionId = UUID.randomUUID();

        service.recordSuccess(user.getId(), sessionId, "10.0.0.1", "JUnit");

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();

        ArgumentCaptor<LoginHistory> saved = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(saved.capture());
        assertThat(saved.getValue().isSuccess()).isTrue();
        assertThat(saved.getValue().getSessionId()).isEqualTo(sessionId);

        verify(auditService).record(eq(AuditAction.LOGIN_SUCCESS), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    @DisplayName("signing in after a lock has lapsed is audited as an unlock")
    void lapsedLockAuditedAsUnlock() {
        user.setLockedUntil(Instant.now().minusSeconds(60));   // lapsed

        service.recordSuccess(user.getId(), UUID.randomUUID(), "10.0.0.1", "JUnit");

        verify(auditService).record(eq(AuditAction.ACCOUNT_UNLOCKED), eq("User"), eq(user.getId()), anyMap());
    }

    @Test
    @DisplayName("a request with no IP skips the throttle rather than failing")
    void nullIpSkipsThrottle() {
        assertThatCode(() -> service.assertNotThrottled("ops@acme.test", null))
                .doesNotThrowAnyException();
        verify(loginHistoryRepository, never()).countRecentFailures(anyString(), anyString(), any());
    }
}
