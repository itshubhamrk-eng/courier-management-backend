package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Device and session lifecycle.
 *
 * <p>A session represents a logged-in device and outlives the refresh tokens issued
 * against it: rotation swaps the token but keeps the session, so "your devices"
 * stays stable across a week of refreshes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties properties;
    private final AuditService auditService;

    /**
     * Opens a session, first evicting the least recently used one if the user is at
     * the concurrency cap.
     *
     * <p>Eviction rather than rejection: telling a user "you are logged in on too
     * many devices, go and log one out" is hostile, and an attacker who has
     * credentials is not stopped by it either way.
     */
    @Transactional
    public UserSession openSession(UUID userId,
                                   Duration lifetime,
                                   boolean rememberMe,
                                   DeviceInfo device) {

        enforceSessionCap(userId);

        Instant now = Instant.now();
        UserSession session = UserSession.builder()
                .userId(userId)
                .deviceId(device.deviceId())
                .deviceName(device.deviceName())
                .deviceType(device.deviceType())
                .ipAddress(device.ipAddress())
                .userAgent(device.userAgent())
                .lastSeenAt(now)
                .expiresAt(now.plus(lifetime))
                .rememberMe(rememberMe)
                .build();

        return sessionRepository.save(session);
    }

    private void enforceSessionCap(UUID userId) {
        List<UserSession> active = sessionRepository.findActiveByUserId(userId, Instant.now());
        int cap = properties.getMaxConcurrentSessions();
        if (active.size() < cap) {
            return;
        }

        // findActiveByUserId returns least-recently-seen first, so the head of the
        // list is exactly what should go. Evict enough to leave room for the new one.
        int toEvict = active.size() - cap + 1;
        for (int i = 0; i < toEvict; i++) {
            UserSession victim = active.get(i);
            revokeSession(victim, UserSession.class.getSimpleName(), RefreshToken.RevokeReason.SESSION_EVICTED);
            log.info("Evicted session {} for user {} (concurrency cap {} reached)",
                    victim.getId(), userId, cap);
        }
    }

    @Transactional
    public void touch(UserSession session) {
        session.touch();
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findActive(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .filter(UserSession::isActive);
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActiveSessions(UUID userId) {
        return sessionRepository.findActiveByUserId(userId, Instant.now());
    }

    /** Revokes one session and every refresh token issued against it. */
    @Transactional
    public void revokeSession(UserSession session, String entityType, String reason) {
        session.revoke(reason);
        sessionRepository.save(session);
        refreshTokenRepository.revokeAllForSession(session.getId(), reason, Instant.now());

        auditService.record(AuditAction.SESSION_REVOKED, entityType, session.getId(),
                Map.of("reason", reason, "userId", session.getUserId().toString()));
    }

    /** "Sign out everywhere" — every device, every token. */
    @Transactional
    public int revokeAllSessions(UUID userId, String reason) {
        Instant now = Instant.now();
        int sessions = sessionRepository.revokeAllForUser(userId, reason, now);
        refreshTokenRepository.revokeAllForUser(userId, reason, now);
        log.info("Revoked {} session(s) for user {} ({})", sessions, userId, reason);
        return sessions;
    }

    /**
     * Used by change-password: the caller keeps working, everyone else is thrown
     * out. If the password was changed because of a suspected compromise, the
     * attacker's sessions die while the legitimate user is not interrupted.
     */
    @Transactional
    public int revokeAllSessionsExcept(UUID userId, UUID keepSessionId, String reason) {
        Instant now = Instant.now();
        int sessions = sessionRepository.revokeAllForUserExcept(userId, keepSessionId, reason, now);
        List<RefreshToken> live = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        for (RefreshToken token : live) {
            if (!token.getSessionId().equals(keepSessionId)) {
                token.revoke(reason);
            }
        }
        refreshTokenRepository.saveAll(live);
        return sessions;
    }

    /**
     * Client-reported device details. All fields are untrusted display data — see
     * {@link UserSession#getDeviceId()}.
     */
    public record DeviceInfo(String deviceId,
                             String deviceName,
                             String deviceType,
                             String ipAddress,
                             String userAgent) {

        public static DeviceInfo of(String deviceId, String deviceName, String ip, String userAgent) {
            return new DeviceInfo(deviceId, deviceName, inferType(userAgent), ip, truncate(userAgent));
        }

        /** Coarse classification for the device list; not security-relevant. */
        private static String inferType(String userAgent) {
            if (userAgent == null || userAgent.isBlank()) {
                return "UNKNOWN";
            }
            String ua = userAgent.toLowerCase();
            if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
                return "MOBILE";
            }
            if (ua.contains("tablet") || ua.contains("ipad")) {
                return "TABLET";
            }
            if (ua.contains("okhttp") || ua.contains("curl") || ua.contains("postman")
                    || ua.contains("java") || ua.contains("python")) {
                return "API_CLIENT";
            }
            return "DESKTOP";
        }

        private static String truncate(String value) {
            if (value == null) {
                return null;
            }
            return value.length() <= 512 ? value : value.substring(0, 512);
        }
    }
}
