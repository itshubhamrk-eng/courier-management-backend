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

    List<RefreshToken> findByFamilyId(UUID familyId);

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
