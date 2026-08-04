package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.LoginFailureReason;
import com.courier.modules.auth.domain.LoginHistory;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Brute-force defence and the login audit trail.
 *
 * <p>Two independent mechanisms, on purpose:
 * <ul>
 *   <li><b>Account lock</b> — after N consecutive failures the account locks for a
 *       window. Protects one account from targeted guessing.</li>
 *   <li><b>Email+IP throttle</b> — counts recent failures in {@code login_history}.
 *       Protects the endpoint from being used as an oracle and from spraying.</li>
 * </ul>
 *
 * <p>Keeping them separate matters: if only the account lock existed, an attacker
 * could cheaply deny service to any user whose email they know. The throttle is
 * keyed by IP as well, so the attacker rate-limits themselves first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserRepository userRepository;
    private final AuthProperties properties;
    private final AuditService auditService;

    /**
     * @throws BusinessRuleException with {@link ErrorCode#RATE_LIMIT_EXCEEDED} when
     *         this email+IP pair has failed too often recently
     */
    @Transactional(readOnly = true)
    public void assertNotThrottled(String email, String ipAddress) {
        if (ipAddress == null) {
            return;
        }
        Instant since = Instant.now().minus(properties.getThrottleWindow());
        long failures = loginHistoryRepository.countRecentFailures(email, ipAddress, since);

        if (failures >= properties.getThrottleMaxAttempts()) {
            log.warn("Login throttled for {} from {} — {} failures in the last {}",
                    email, ipAddress, failures, properties.getThrottleWindow());
            throw new BusinessRuleException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many failed sign-in attempts. Try again later.");
        }
    }

    /**
     * Records a failure and, when the user is known, advances the lock counter.
     *
     * <p>{@code REQUIRES_NEW} so the record survives the rollback of the failed
     * login transaction — otherwise every failed attempt would erase its own
     * evidence, and the throttle would never trigger.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID companyId,
                              User user,
                              String attemptedEmail,
                              LoginFailureReason reason,
                              String ipAddress,
                              String userAgent) {

        loginHistoryRepository.save(LoginHistory.builder()
                .userId(user != null ? user.getId() : null)
                .attemptedEmail(attemptedEmail)
                .success(false)
                .failureReason(reason)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .occurredAt(Instant.now())
                .build());

        if (user != null && reason == LoginFailureReason.BAD_PASSWORD) {
            boolean wasLocked = user.isTemporarilyLocked();
            user.registerFailedAttempt(properties.getLockoutThreshold(), properties.getLockoutDuration());
            userRepository.save(user);

            if (!wasLocked && user.isTemporarilyLocked()) {
                log.warn("Account {} locked until {} after {} failed attempts",
                        user.getId(), user.getLockedUntil(), user.getFailedAttempts());
                auditService.record(AuditAction.ACCOUNT_LOCKED, "User", user.getId(),
                        Map.of("failedAttempts", user.getFailedAttempts(),
                                "lockedUntil", String.valueOf(user.getLockedUntil()),
                                "ipAddress", String.valueOf(ipAddress)));
            }
        }

        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason.name());
        details.put("email", attemptedEmail);
        details.put("ipAddress", ipAddress);
        auditService.record(AuditAction.LOGIN_FAILURE, "User",
                user != null ? user.getId() : null, details, false);
    }

    /** Records the successful attempt and clears the failure counters. */
    @Transactional
    public void recordSuccess(User user, UUID sessionId, String ipAddress, String userAgent) {
        boolean wasLocked = user.getLockedUntil() != null;

        user.registerSuccessfulLogin(ipAddress);
        userRepository.save(user);

        loginHistoryRepository.save(LoginHistory.builder()
                .userId(user.getId())
                .attemptedEmail(user.getEmail())
                .success(true)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .occurredAt(Instant.now())
                .build());

        if (wasLocked) {
            // The lock had lapsed and a valid password arrived: the account is
            // effectively unlocked, which is worth its own audit line.
            auditService.record(AuditAction.ACCOUNT_UNLOCKED, "User", user.getId(),
                    Map.of("trigger", "SUCCESSFUL_LOGIN_AFTER_LOCK_EXPIRY"));
        }

        auditService.record(AuditAction.LOGIN_SUCCESS, "User", user.getId(),
                Map.of("sessionId", String.valueOf(sessionId), "ipAddress", String.valueOf(ipAddress)));
    }
}
