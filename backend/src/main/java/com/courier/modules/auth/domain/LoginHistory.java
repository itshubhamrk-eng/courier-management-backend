package com.courier.modules.auth.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Append-only record of every login attempt, successful or not.
 *
 * <p>Serves two purposes: a user-visible "recent activity" list, and the input to
 * the login throttle. Failures are recorded with {@code userId} null when the email
 * matched nobody — the attempted email is still stored so repeated probing of a
 * non-existent account is visible.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "login_history", indexes = {
        // The throttle query: failures for this email+IP inside the window.
        @Index(name = "idx_login_history_email_time", columnList = "company_id, attempted_email, occurred_at"),
        @Index(name = "idx_login_history_user_time", columnList = "company_id, user_id, occurred_at")
})
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class LoginHistory extends CompanyOwnedEntity {

    /** Null when the attempted email matched no account. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "attempted_email", nullable = false, length = 255)
    private String attemptedEmail;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private LoginFailureReason failureReason;

    /** V86. Classifies this row beyond the {@code success} boolean — the same table now
     *  carries LOGOUT and SESSION_EXPIRED rows too. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 20, nullable = false)
    @Builder.Default
    private LoginEventType eventType = LoginEventType.LOGIN_SUCCESS;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_id", columnDefinition = "BINARY(16)")
    private UUID sessionId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** V86, parsed from {@code userAgent} at write time — see {@code UserAgentParser}. */
    @Column(name = "device", length = 30)
    private String device;

    @Column(name = "browser", length = 60)
    private String browser;

    @Column(name = "os", length = 60)
    private String os;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** V86. Set when a later LOGOUT/SESSION_EXPIRED row for the same session finds this
     *  one — best-effort, see the migration's own note. Null while the session is live. */
    @Column(name = "logout_at")
    private Instant logoutAt;
}
