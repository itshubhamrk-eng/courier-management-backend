package com.courier.modules.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * <b>Deliberately crosses companies.</b> A reset link is followed by an
     * unauthenticated browser, so no company is bound and the Hibernate filter
     * cannot narrow this query — that is by design, not an oversight.
     *
     * <p>Safe because the lookup key is the SHA-256 of 32 bytes of
     * {@code SecureRandom}: unguessable, and useless if the table leaks. The company
     * is bound from the row that is found, before any further work happens.
     *
     * <p>Documented exception to the company-filter invariant in
     * {@code MEMORY/AI_CONTEXT.md}.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates outstanding links when a new one is requested, so only the most
     * recent email works.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update PasswordResetToken t
              set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
           """)
    int consumeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
