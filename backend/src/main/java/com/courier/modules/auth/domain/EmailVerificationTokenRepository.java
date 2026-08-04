package com.courier.modules.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * <b>Deliberately crosses companies</b>, for the same reason as
     * {@link PasswordResetTokenRepository#findByTokenHash}: the verification link is
     * followed by an unauthenticated browser. The key is the hash of 32
     * cryptographically random bytes, and the company is bound from the row found.
     */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Most recent unconsumed token for a user — used to rate-limit re-sends so a
     * repeated login attempt does not mail the user on every try.
     */
    @Query("""
           select t from EmailVerificationToken t
            where t.userId = :userId and t.consumedAt is null
            order by t.createdAt desc
            limit 1
           """)
    Optional<EmailVerificationToken> findLatestPendingForUser(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update EmailVerificationToken t
              set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
           """)
    int consumeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
