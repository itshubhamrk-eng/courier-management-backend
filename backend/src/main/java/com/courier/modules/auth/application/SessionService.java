package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final DataSource dataSource;

    /** How long a login waits for another concurrent login (same user) to finish
     * its own count-evict-create sequence before giving up on cap enforcement for
     * this round. Generous relative to the sub-millisecond work actually done
     * under the lock — only matters if something is badly wedged. */
    private static final int SESSION_CAP_LOCK_TIMEOUT_SECONDS = 5;

    /**
     * Self-injected proxy, needed only so {@link #enforceSessionCap} can call
     * {@link #revokeSession} <em>through the Spring AOP proxy</em> rather than by
     * plain self-invocation — {@code @Transactional(REQUIRES_NEW)} has no effect on
     * a method called as {@code this.revokeSession(...)} from inside the same class,
     * since there is no proxy in the way to intercept that call.
     *
     * <p>Field injection, not this class's usual constructor injection via
     * {@code @RequiredArgsConstructor}: Lombok does not copy a field's {@code @Lazy}
     * onto the constructor parameter it generates, so a constructor-injected
     * {@code self} here would make Spring reject the circular reference outright at
     * startup instead of deferring it — confirmed by a real boot failure
     * ("Relying upon circular references is discouraged...") getting this wrong the
     * first time.
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private SessionService self;

    /**
     * Opens a session, first evicting the least recently used one if the user is at
     * the concurrency cap.
     *
     * <p>Eviction rather than rejection: telling a user "you are logged in on too
     * many devices, go and log one out" is hostile, and an attacker who has
     * credentials is not stopped by it either way.
     *
     * <p><b>Concurrency</b>: two logins for the same user racing this method can
     * both read "under the cap" and both create a session, landing over the
     * configured limit (ISSUE-008: found under a k6 load test, 10 concurrent
     * logins to one account could land at 9 active sessions instead of 5). Fixed
     * by holding a MySQL named lock ({@code GET_LOCK}/{@code RELEASE_LOCK}) keyed
     * per user id around the count-evict-create sequence, so only one login for
     * <em>that user</em> runs it at a time — a different user's login is on a
     * different lock name and proceeds fully in parallel. Deliberately not a lock
     * on the {@code users} row itself: an earlier attempt at that serialized far
     * more than session creation (anything else touching that row) and caused
     * real lock-wait timeouts on otherwise-legitimate concurrent logins. A named
     * lock has no such blast radius — it means nothing to any other query.
     *
     * <p>The lock is held on a {@link Connection} obtained directly from the
     * {@link DataSource}, deliberately <em>not</em> via {@code JdbcTemplate} (a
     * first attempt at this used {@code JdbcTemplate}, on the assumption that it
     * would transparently share the connection {@code @Transactional} already has
     * bound for JPA — it does not reliably do so here, so {@code GET_LOCK} and
     * {@code RELEASE_LOCK} could silently land on two different physical
     * connections, breaking mutual exclusion between concurrent logins entirely
     * with no error raised. Live testing caught this: the cap still overran even
     * with the "lock" in place). A dedicated {@code Connection}, opened and closed
     * by this method alone, has no such ambiguity — GET_LOCK/RELEASE_LOCK always
     * run on the exact same physical MySQL session.
     *
     * <p><b>This method itself is deliberately not {@code @Transactional}.</b> The
     * count-evict-create work runs in {@link #openSessionLocked}, called through
     * {@code self} so Spring's proxy commits that transaction before the call
     * returns here. That ordering matters: a first version put the lock and the
     * transaction on the <em>same</em> method, so the lock was released (in a
     * {@code finally} inside the transactional method body) <em>before</em> the
     * surrounding {@code @Transactional} proxy actually committed — a still-open
     * gap between "lock released" and "write visible to the next lock holder"
     * that let the cap overrun exactly as before, just in a narrower window. Live
     * testing caught that too (consistently landed at 6, not 5). Holding the lock
     * around the whole transactional call, not just the work inside it, closes
     * that gap: nothing else can even attempt this user's count-evict-create
     * sequence until the previous one has fully committed.
     *
     * <p>If the lock cannot be acquired within {@link #SESSION_CAP_LOCK_TIMEOUT_SECONDS}
     * (never observed in testing at this cap/concurrency; would need pathological
     * contention), the login still proceeds without cap enforcement for that one
     * request rather than failing it — the cap is a soft UX limit, not a security
     * boundary, and a login must never hard-fail because of it.
     */
    public UserSession openSession(UUID userId,
                                   Duration lifetime,
                                   boolean rememberMe,
                                   DeviceInfo device) {

        try (Connection lockConnection = dataSource.getConnection()) {
            boolean locked = tryAcquireSessionCapLock(lockConnection, userId);
            if (!locked) {
                log.warn("Timed out acquiring session-cap lock for user {} after {}s — proceeding without cap enforcement for this login",
                        userId, SESSION_CAP_LOCK_TIMEOUT_SECONDS);
            }
            try {
                return self.openSessionLocked(userId, lifetime, rememberMe, device);
            } finally {
                if (locked) {
                    releaseSessionCapLock(lockConnection, userId);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to manage the session-cap lock connection for user " + userId, e);
        }
    }

    /**
     * The actual count-evict-create sequence, in its own transaction so it commits
     * (making its write visible to the next lock holder) before {@link #openSession}
     * releases the lock. Not called directly — only through {@code self} from
     * {@link #openSession}, so the {@code @Transactional} proxy is in the loop; see
     * that method's javadoc for why the ordering matters. Public for the same
     * proxy-visibility reason as {@link #revokeSession}.
     */
    @Transactional
    public UserSession openSessionLocked(UUID userId,
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

    private boolean tryAcquireSessionCapLock(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, sessionCapLockName(userId));
            ps.setInt(2, SESSION_CAP_LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long result = rs.getLong(1);
                return !rs.wasNull() && result == 1L;
            }
        }
    }

    private void releaseSessionCapLock(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, sessionCapLockName(userId));
            ps.executeQuery();
        }
    }

    private static String sessionCapLockName(UUID userId) {
        return "session_cap:" + userId;
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
            UUID victimId = active.get(i).getId();
            try {
                // Through self, in its own transaction (REQUIRES_NEW) — two logins
                // for the same user racing to evict the same victim session is a real
                // scenario (found running a k6 load test), not just a theoretical one;
                // an optimistic-lock loss here means a concurrent request already
                // evicted or otherwise touched this exact session, so the eviction's
                // own goal is already satisfied (or about to be) either way. Isolating
                // it in its own transaction means that loss can never poison the
                // caller's own transaction that is about to create the new session.
                //
                // By id, not the `UserSession` object `active` already holds: passing
                // that object across the transaction boundary would let revokeSession's
                // in-place mutation (session.revoke(...)) make *this* method's own
                // outer persistence context see the same object as dirty too — a second
                // flush of the same row, racing the nested transaction's already-
                // committed update. revokeSession re-fetches its own copy instead, so
                // the two persistence contexts never share a mutable instance.
                self.revokeSession(victimId, UserSession.class.getSimpleName(), RefreshToken.RevokeReason.SESSION_EVICTED);
                log.info("Evicted session {} for user {} (concurrency cap {} reached)",
                        victimId, userId, cap);
            } catch (ConcurrencyFailureException e) {
                log.info("Session {} for user {} was already evicted by a concurrent login — skipping",
                        victimId, userId);
            }
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

    /**
     * Revokes one session and every refresh token issued against it.
     *
     * <p>{@code REQUIRES_NEW} so a caller that only wants "best effort, isolated"
     * revocation (see {@link #enforceSessionCap}) can catch a lost optimistic-lock
     * race here without it poisoning their own, separate transaction.
     *
     * <p>Takes an id, not a {@code UserSession} — re-fetches its own copy in this
     * (new) persistence context rather than accepting one the caller may have loaded
     * in a <em>different</em> one. Mutating a caller-owned entity here would make the
     * caller's own persistence context see it as dirty too, once this transaction
     * commits — a second, conflicting flush of the same row from two different
     * transactions, defeating the whole point of {@code REQUIRES_NEW} isolation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeSession(UUID sessionId, String entityType, String reason) {
        UserSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.isActive()) {
            return; // already revoked (or gone) — someone else's job here is done
        }

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
