package com.courier.modules.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User user() {
        return User.builder()
                .email("ops@acme.test")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("locks once the failure threshold is reached")
    void locksAtThreshold() {
        User user = user();
        for (int i = 0; i < 4; i++) {
            user.registerFailedAttempt(5, Duration.ofMinutes(15));
            assertThat(user.isTemporarilyLocked()).isFalse();
        }
        user.registerFailedAttempt(5, Duration.ofMinutes(15));

        assertThat(user.isTemporarilyLocked()).isTrue();
        assertThat(user.getFailedAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("a lapsed lock reports unlocked without any job running")
    void lockSelfClears() {
        User user = user();
        user.setLockedUntil(Instant.now().minusSeconds(1));

        // This is what makes the lock self-clearing: no scheduler is involved.
        assertThat(user.isTemporarilyLocked()).isFalse();
    }

    @Test
    @DisplayName("a successful login clears the counters")
    void successResetsCounters() {
        User user = user();
        user.registerFailedAttempt(5, Duration.ofMinutes(15));
        user.registerFailedAttempt(5, Duration.ofMinutes(15));

        user.registerSuccessfulLogin("10.0.0.1");

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginIp()).isEqualTo("10.0.0.1");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("password expiry is disabled when max age is zero")
    void expiryDisabledByDefault() {
        User user = user();
        user.setPasswordChangedAt(Instant.now().minus(Duration.ofDays(3650)));

        assertThat(user.isPasswordExpired(Duration.ZERO)).isFalse();
        assertThat(user.isPasswordExpired(null)).isFalse();
    }

    @Test
    @DisplayName("password expires once older than the configured age")
    void expiresWhenConfigured() {
        User user = user();
        user.setPasswordChangedAt(Instant.now().minus(Duration.ofDays(100)));

        assertThat(user.isPasswordExpired(Duration.ofDays(90))).isTrue();
        assertThat(user.isPasswordExpired(Duration.ofDays(365))).isFalse();
    }

    @Test
    @DisplayName("verifying the email promotes a PENDING account to ACTIVE")
    void verificationActivates() {
        User user = user();
        user.setStatus(UserStatus.PENDING);

        user.markEmailVerified();

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("email is normalised to lowercase")
    void emailNormalisation() {
        assertThat(User.normaliseEmail("  OPS@Acme.TEST ")).isEqualTo("ops@acme.test");
        assertThat(User.normaliseEmail(null)).isNull();
    }

    @Test
    @DisplayName("DISABLED and LOCKED both count as disabled for sign-in")
    void disabledStates() {
        User user = user();
        user.setStatus(UserStatus.DISABLED);
        assertThat(user.isDisabled()).isTrue();

        user.setStatus(UserStatus.LOCKED);
        assertThat(user.isDisabled()).isTrue();

        user.setStatus(UserStatus.ACTIVE);
        assertThat(user.isDisabled()).isFalse();
    }
}
