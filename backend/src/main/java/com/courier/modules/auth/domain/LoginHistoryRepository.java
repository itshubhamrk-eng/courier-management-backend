package com.courier.modules.auth.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    Page<LoginHistory> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
}
