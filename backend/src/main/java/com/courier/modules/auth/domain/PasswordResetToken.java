package com.courier.modules.auth.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use password reset token.
 *
 * <p>Only the SHA-256 hash of the token is stored, so a database leak yields
 * nothing usable. The raw value is 32 bytes of {@code SecureRandom} and exists
 * only in the link sent to the user.
 *
 * <p>Consumed exactly once: {@code consumedAt} is stamped inside the same
 * transaction that applies the effect, so a replayed link is inert.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_reset_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_reset_tokens_user", columnList = "company_id, user_id")
})
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PasswordResetToken extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    /** SHA-256 of the raw token, hex encoded. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsable() {
        return !isConsumed() && !isExpired();
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
