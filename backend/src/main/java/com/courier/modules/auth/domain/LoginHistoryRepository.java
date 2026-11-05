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
     * Scanner input (CREDENTIAL_ATTACK, IP level): failed logins in {@code [from, to)} grouped by
     * (IP, company). Only {@code LOGIN_FAILED} rows count — {@code SESSION_EXPIRED} rows are also
     * {@code success = false} but are not login attempts. Every {@code recordFailure} reason is
     * included, GEO_RESTRICTED among them. Cross-company on purpose; uses
     * {@code idx_login_history_event_time} (V143).
     */
    @Query("""
           select new com.courier.modules.auth.domain.LoginFailureIpCount(
                      h.ipAddress, h.companyId, count(distinct h.attemptedEmail), count(h))
             from LoginHistory h
            where h.eventType = com.courier.modules.auth.domain.LoginEventType.LOGIN_FAILED
              and h.success = false
              and h.occurredAt >= :from and h.occurredAt < :to
              and h.ipAddress is not null
            group by h.ipAddress, h.companyId
           """)
    List<LoginFailureIpCount> findLoginFailuresByIp(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Scanner input (CREDENTIAL_ATTACK, account level): accounts (company + attempted email)
     * with at least {@code threshold} failed logins in {@code [from, to)}, from any IP. Same row
     * selection and index as {@link #findLoginFailuresByIp}.
     */
    @Query("""
           select new com.courier.modules.auth.domain.LoginFailureAccountCount(
                      h.companyId, h.attemptedEmail, count(h), count(distinct h.ipAddress))
             from LoginHistory h
            where h.eventType = com.courier.modules.auth.domain.LoginEventType.LOGIN_FAILED
              and h.success = false
              and h.occurredAt >= :from and h.occurredAt < :to
            group by h.companyId, h.attemptedEmail
           having count(h) >= :threshold
           """)
    List<LoginFailureAccountCount> findAccountsWithLoginFailuresAtLeast(@Param("from") Instant from,
                                                                        @Param("to") Instant to,
                                                                        @Param("threshold") long threshold);

    Page<LoginHistory> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    /** Finds the LOGIN_SUCCESS row a LOGOUT/SESSION_EXPIRED event should close out. */
    java.util.Optional<LoginHistory> findFirstByUserIdAndSessionIdAndEventTypeOrderByOccurredAtDesc(
            UUID userId, UUID sessionId, LoginEventType eventType);
}
