package com.courier.modules.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Authentication policy, bound from {@code app.auth.*}.
 *
 * <p>Durations use {@code @NotNull} rather than {@code @Positive}: Bean Validation
 * has no {@code Positive} validator for {@link Duration} and fails at startup with
 * {@code HV000030} — the same trap recorded as decision 9 in
 * {@code MEMORY/AI_CONTEXT.md}. Range checks live in {@link #validate()}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Consecutive failures before the account locks itself. */
    @Min(1)
    private int lockoutThreshold = 5;

    /** How long a lock holds. It lapses on its own; no unlock job is needed. */
    @NotNull
    private Duration lockoutDuration = Duration.ofMinutes(15);

    /**
     * Failures per email+IP before the endpoint starts refusing outright.
     * Higher than the lockout threshold on purpose: the account lock protects one
     * account, this protects the endpoint from being used as an oracle.
     */
    @Min(1)
    private int throttleMaxAttempts = 10;

    @NotNull
    private Duration throttleWindow = Duration.ofMinutes(15);

    /** Beyond this, the least recently used session is evicted on a new login. */
    @Min(1)
    private int maxConcurrentSessions = 5;

    /** Refresh-token and session lifetime when "remember me" is requested. */
    @NotNull
    private Duration rememberMeDuration = Duration.ofDays(30);

    @NotNull
    private Duration resetTokenTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration verificationTokenTtl = Duration.ofHours(24);

    /** Minimum gap between re-issued verification mails for the same user. */
    @NotNull
    private Duration verificationResendWindow = Duration.ofMinutes(5);

    /** Base URL the emailed links point at. */
    private String appBaseUrl = "http://localhost:3000";

    @NotNull
    private PasswordPolicyProperties password = new PasswordPolicyProperties();

    @Getter
    @Setter
    public static class PasswordPolicyProperties {

        @Min(8)
        private int minLength = 10;

        /**
         * BCrypt silently ignores anything past 72 bytes. Capping below that stops
         * a user believing a 200-character passphrase is fully protecting them.
         */
        @Min(16)
        private int maxLength = 72;

        private boolean requireDigit = true;

        private boolean requireLetter = true;

        private boolean requireMixedCase = false;

        private boolean requireSpecial = false;

        /** Reject passwords appearing in the bundled common-password list. */
        private boolean rejectCommonPasswords = true;

        /**
         * Forced rotation. Zero disables it, which is the default: NIST SP 800-63B
         * advises against periodic expiry because it drives predictable increments.
         */
        @NotNull
        private Duration maxAge = Duration.ZERO;
    }

    /**
     * Range checks Bean Validation cannot express for {@link Duration}. Called from
     * {@code AuthService}'s initialiser so a nonsensical configuration stops the
     * application rather than silently misbehaving.
     */
    public void validate() {
        requirePositive(lockoutDuration, "app.auth.lockout-duration");
        requirePositive(throttleWindow, "app.auth.throttle-window");
        requirePositive(rememberMeDuration, "app.auth.remember-me-duration");
        requirePositive(resetTokenTtl, "app.auth.reset-token-ttl");
        requirePositive(verificationTokenTtl, "app.auth.verification-token-ttl");
        if (verificationResendWindow.isNegative()) {
            throw new IllegalStateException("app.auth.verification-resend-window must not be negative");
        }
        if (password.getMaxLength() < password.getMinLength()) {
            throw new IllegalStateException(
                    "app.auth.password.max-length must be >= min-length");
        }
    }

    private static void requirePositive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(
                    "%s must be a positive duration (got %s)".formatted(key, value));
        }
    }
}
