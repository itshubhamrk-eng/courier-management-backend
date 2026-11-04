package com.courier.modules.security.application;

import com.courier.modules.auth.application.SessionService;
import com.courier.modules.auth.application.TokenRevocationService.RevocableTokenStore;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Disables / re-enables a user as a security measure. A ban cuts off both halves of
 * a session: refresh tokens (MySQL, via {@link SessionService}) and live access
 * tokens (Redis user-level ban key), so it takes effect immediately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBanService {

    /** Added to the access-token TTL so a token issued just before the ban cannot outlive the key. */
    private static final Duration BAN_KEY_MARGIN = Duration.ofMinutes(5);

    static final String SESSION_REVOKE_REASON = "SECURITY_BAN";

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final ObjectProvider<RevocableTokenStore> tokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    /** Idempotent: banning an already disabled user repeats the (harmless) revocations. */
    @Transactional
    public void ban(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getStatus() != UserStatus.DISABLED) {
            user.disable();
            userRepository.save(user);
        }
        sessionService.revokeAllSessions(userId, SESSION_REVOKE_REASON);

        RevocableTokenStore store = tokenStore.getIfAvailable();
        if (store != null) {
            try {
                store.banUser(userId, jwtTokenProvider.accessTokenTtl().plus(BAN_KEY_MARGIN));
            } catch (RuntimeException e) {
                // The refresh tokens are already revoked and the account disabled; a Redis
                // outage only leaves live access tokens valid for their short TTL.
                log.warn("Could not write ban key for user {}: {}", userId, e.toString());
            }
        }

        auditService.record(AuditAction.USER_BANNED, "User", userId,
                Map.of("reason", reason == null ? "" : reason));
    }

    @Transactional
    public void unban(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.reactivate();
        userRepository.save(user);

        RevocableTokenStore store = tokenStore.getIfAvailable();
        if (store != null) {
            try {
                store.unbanUser(userId);
            } catch (RuntimeException e) {
                log.warn("Could not clear ban key for user {}: {}", userId, e.toString());
            }
        }

        auditService.record(AuditAction.USER_UNBANNED, "User", userId);
    }
}
