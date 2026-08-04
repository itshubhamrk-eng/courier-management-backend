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
 * One logged-in device.
 *
 * <p>This is what makes "you are signed in on 3 devices" and "sign out everywhere"
 * answerable. A session outlives individual refresh tokens: rotation replaces the
 * token but keeps the session, so the user's device list is stable.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user", columnList = "company_id, user_id, revoked_at"),
        @Index(name = "idx_user_sessions_expiry", columnList = "expires_at")
})
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class UserSession extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    /**
     * Client-supplied device identifier. Not a trust boundary: spoofing it only
     * affects the attacker's own view of their session list, so it is accepted
     * unverified and used purely for display and de-duplication.
     */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Bumped on every refresh; drives least-recently-used session eviction. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 60)
    private String revokedReason;

    /** Extends the session and its refresh tokens to the remember-me duration. */
    @Column(name = "remember_me", nullable = false)
    @Builder.Default
    private boolean rememberMe = false;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public void revoke(String reason) {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }
}
