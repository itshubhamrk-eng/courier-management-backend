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
 * One issued refresh token.
 *
 * <p>Only the SHA-256 hash of the token is stored. A refresh token is a bearer
 * credential valid for days, so a database leak must not hand an attacker working
 * tokens — the same reason passwords are hashed. Lookup is therefore by hash, and
 * the raw value exists only in the response body and the client's storage.
 *
 * <p>Rotation forms a <b>family</b>: every token minted from the same original login
 * shares a {@code familyId}. Presenting an already-revoked token means it was
 * replayed, so the whole family is destroyed rather than just that one row. This is
 * what makes theft of a refresh token self-limiting.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_tokens_user", columnList = "company_id, user_id, revoked_at"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id"),
        @Index(name = "idx_refresh_tokens_expiry", columnList = "expires_at")
})
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class RefreshToken extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    /** The session this token belongs to; revoking the session revokes the token. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID sessionId;

    /** Shared by every token descended from one login. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "family_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID familyId;

    /** The token's {@code jti} claim, for correlation with logs and audit. */
    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    /** SHA-256 of the raw token, hex encoded. Never the token itself. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 60)
    private String revokedReason;

    /** Set on rotation, so a replay can be traced to its successor. */
    @Column(name = "replaced_by_jti", length = 64)
    private String replacedByJti;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public void revoke(String reason) {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }

    public void rotateTo(String successorJti) {
        revoke(RevokeReason.ROTATED);
        this.replacedByJti = successorJti;
    }

    /** Reasons are constants so audit queries can group on them reliably. */
    public static final class RevokeReason {
        public static final String ROTATED = "ROTATED";
        public static final String LOGOUT = "LOGOUT";
        public static final String LOGOUT_ALL = "LOGOUT_ALL_DEVICES";
        public static final String REUSE_DETECTED = "REUSE_DETECTED";
        public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
        public static final String PASSWORD_RESET = "PASSWORD_RESET";
        public static final String SESSION_EVICTED = "SESSION_EVICTED";

        private RevokeReason() {
        }
    }
}
