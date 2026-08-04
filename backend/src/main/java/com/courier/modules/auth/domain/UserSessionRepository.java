package com.courier.modules.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /**
     * Live sessions, oldest contact first. The ordering is what the session cap
     * relies on: when the limit is exceeded, the least recently seen device is the
     * one evicted.
     */
    @Query("""
           select s from UserSession s
            where s.userId = :userId
              and s.revokedAt is null
              and s.expiresAt > :now
            order by s.lastSeenAt asc
           """)
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update UserSession s
              set s.revokedAt = :now, s.revokedReason = :reason
            where s.userId = :userId and s.revokedAt is null
           """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("reason") String reason,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update UserSession s
              set s.revokedAt = :now, s.revokedReason = :reason
            where s.userId = :userId and s.id <> :keepSessionId and s.revokedAt is null
           """)
    int revokeAllForUserExcept(@Param("userId") UUID userId,
                               @Param("keepSessionId") UUID keepSessionId,
                               @Param("reason") String reason,
                               @Param("now") Instant now);
}
