package com.courier.modules.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Primary lookup on refresh. The company is already bound from the token's
     * {@code tid} claim before this runs, so the Hibernate filter applies and a
     * token cannot be redeemed against the wrong company.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findByFamilyId(UUID familyId);

    /**
     * Scanner input: users with more than {@code threshold} replays, inside {@code [from, to)},
     * of an already-revoked refresh token that had NO rotation successor (revoked by logout,
     * eviction, ban, ... and presented again). {@code TokenIssuer#revokeFamilyAndAudit} writes
     * one REFRESH_TOKEN_REUSE_DETECTED audit row per replay with {@code entity_id} = the
     * replayed token's id; the join to that token supplies the company and user (the audit
     * row's details JSON is never queried). Tokens with a successor ({@code replaced_by_jti}
     * set) are rotation reuse, which the REFRESH_TOKEN_REUSE rule owns. Cross-company on
     * purpose; the audit side uses {@code idx_audit_logs_action (action, occurred_at)}.
     */
    @Query("""
           select new com.courier.modules.auth.domain.UserEventCount(t.companyId, t.userId, count(a))
             from AuditLog a, RefreshToken t
            where a.action = com.courier.shared.audit.domain.AuditAction.REFRESH_TOKEN_REUSE_DETECTED
              and a.occurredAt >= :from and a.occurredAt < :to
              and t.id = a.entityId
              and t.replacedByJti is null
            group by t.companyId, t.userId
           having count(a) > :threshold
           """)
    List<UserEventCount> findUsersWithRevokedTokenReplaysAbove(@Param("from") Instant from,
                                                               @Param("to") Instant to,
                                                               @Param("threshold") long threshold);

    /**
     * Scanner input: every token issued in {@code [from, to)} (login and each rotation), as
     * the (session, user agent, ip) it was issued to. Cross-company on purpose — the scanner
     * runs with no company bound. Needs {@code idx_refresh_tokens_created_at} (V141).
     */
    @Query("""
           select distinct new com.courier.modules.auth.domain.SessionRefreshEvent(
                      t.companyId, t.userId, t.sessionId, t.userAgent, t.ipAddress)
             from RefreshToken t
            where t.createdAt >= :from and t.createdAt < :to
           """)
    List<SessionRefreshEvent> findSessionRefreshEvents(@Param("from") Instant from, @Param("to") Instant to);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    List<RefreshToken> findBySessionIdAndRevokedAtIsNull(UUID sessionId);

    /**
     * Revokes an entire rotation family in one statement. Used on replay detection,
     * where speed matters: the attacker and the victim are both holding tokens and
     * whichever we miss stays usable.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update RefreshToken t
              set t.revokedAt = :now, t.revokedReason = :reason
            where t.familyId = :familyId and t.revokedAt is null
           """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("reason") String reason,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update RefreshToken t
              set t.revokedAt = :now, t.revokedReason = :reason
            where t.userId = :userId and t.revokedAt is null
           """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("reason") String reason,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update RefreshToken t
              set t.revokedAt = :now, t.revokedReason = :reason
            where t.sessionId = :sessionId and t.revokedAt is null
           """)
    int revokeAllForSession(@Param("sessionId") UUID sessionId,
                            @Param("reason") String reason,
                            @Param("now") Instant now);
}
