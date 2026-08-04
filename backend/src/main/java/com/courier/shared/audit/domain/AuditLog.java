package com.courier.shared.audit.domain;

import com.courier.shared.domain.BaseEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only record of a security- or business-significant event.
 *
 * <p>Extends {@link BaseEntity}, <b>not</b> {@code CompanyOwnedEntity}, on purpose:
 * the audit trail must also capture events that happen outside a company binding
 * (failed logins, platform-admin actions, company creation itself). The company is
 * recorded as an ordinary nullable column, and platform queries can therefore read
 * across companies — which is the point of an audit log.
 *
 * <p>Never updated or deleted by application code. The soft-delete columns inherited
 * from {@code BaseEntity} exist only for schema uniformity.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_company_time", columnList = "company_id, occurred_at"),
        @Index(name = "idx_audit_logs_actor", columnList = "actor_id, occurred_at"),
        @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_logs_action", columnList = "action, occurred_at")
})
public class AuditLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private AuditAction action;

    /** Null for platform-level or pre-authentication events. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    /** Null for anonymous events such as a failed login. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "actor_id", columnDefinition = "BINARY(16)")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    /** Simple class name of the affected aggregate, e.g. {@code Shipment}. */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "entity_id", columnDefinition = "BINARY(16)")
    private UUID entityId;

    /** Free-form JSON detail. Must never contain credentials, tokens or full PII. */
    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Correlates the event with the application logs for the same request. */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "success", nullable = false)
    private boolean success;
}
