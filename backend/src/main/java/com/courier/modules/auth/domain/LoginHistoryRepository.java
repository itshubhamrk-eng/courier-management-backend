package com.courier.modules.auth.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    /**
     * Backs the login throttle.
     *
     * <p>Counting persisted failures rather than a Redis counter means the throttle
     * survives a cache restart and cannot be reset by an attacker who can flush
     * Redis. It costs one indexed count per login attempt — covered by
     * {@code idx_login_history_email_time}.
     */
    @Query("""
           select count(h) from LoginHistory h
            where h.attemptedEmail = :email
              and h.ipAddress = :ipAddress
              and h.success = false
              and h.occurredAt > :since
           """)
    long countRecentFailures(@Param("email") String email,
                             @Param("ipAddress") String ipAddress,
                             @Param("since") Instant since);

    /**
     * Scanner input: users with more than {@code threshold} SESSION_EXPIRED events in
     * {@code [from, to)} — refresh attempts against a session that was revoked or expired.
     * Cross-company on purpose (the scanner runs with no company bound).
     */
    @Query("""
           select new com.courier.modules.auth.domain.UserEventCount(h.companyId, h.userId, count(h))
             from LoginHistory h
            where h.eventType = com.courier.modules.auth.domain.LoginEventType.SESSION_EXPIRED
              and h.userId is not null
              and h.occurredAt >= :from and h.occurredAt < :to
            group by h.companyId, h.userId
           having count(h) > :threshold
           """)
    List<UserEventCount> findUsersWithSessionExpiredAbove(@Param("from") Instant from,
                                                          @Param("to") Instant to,
                                                          @Param("threshold") long threshold);

    Page<LoginHistory> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    /** Finds the LOGIN_SUCCESS row a LOGOUT/SESSION_EXPIRED event should close out. */
    java.util.Optional<LoginHistory> findFirstByUserIdAndSessionIdAndEventTypeOrderByOccurredAtDesc(
            UUID userId, UUID sessionId, LoginEventType eventType);
}
