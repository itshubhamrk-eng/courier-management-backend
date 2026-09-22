package com.courier.shared.activity.domain;

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
 * One row per user action inside the application — the WHO/DID WHAT/ON WHICH RECORD/
 * WHEN/FROM WHERE/WHAT CHANGED trail the User Activity &amp; Audit Logging module exists
 * to keep.
 *
 * <p>Deliberately a sibling of {@code shared.audit.domain.AuditLog}, not a replacement:
 * {@code AuditLog} is the curated, hand-called-out security/business trail 60+ services
 * already write to (login, money movement, permission grants, ...). This table is the
 * <em>automatic</em>, request-shaped complement — {@code ActivityLoggingFilter} writes one
 * row per authenticated API call with no controller changes required, module/submodule/
 * action inferred from the route, and carries fields {@code AuditLog} has no room for:
 * separate old/new value JSON, module/submodule, request method + endpoint, and status +
 * error message. A single write path in the codebase, whichever table, would have forced
 * either bloating the existing curated trail with request noise or losing the rich detail
 * 60+ call sites already carry — two tables, two jobs.
 *
 * <p>Extends {@link BaseEntity}, not {@code CompanyOwnedEntity}, for the same reason
 * {@code AuditLog} does: {@code companyId} must stay populatable for platform-tier and
 * pre/post-auth edge cases without a Hibernate filter fighting a null bind. Company
 * isolation for reads is enforced explicitly in {@code ActivityLogService} /
 * {@code ActivityLogSpecifications}, never left to the filter.
 *
 * <p>Never updated or deleted by application code — see the class invariant this module
 * documents in {@code MEMORY/modules/activity-log.md} §"Immutability".
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "activity_logs", indexes = {
        @Index(name = "idx_activity_logs_company_time", columnList = "company_id, occurred_at"),
        @Index(name = "idx_activity_logs_user_time", columnList = "user_id, occurred_at"),
        @Index(name = "idx_activity_logs_module_time", columnList = "module, occurred_at"),
        @Index(name = "idx_activity_logs_action_time", columnList = "action, occurred_at"),
        @Index(name = "idx_activity_logs_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_activity_logs_session", columnList = "session_id")
})
public class ActivityLog extends BaseEntity {

    /** Null only for platform-level activity with no company binding. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "username", length = 255)
    private String username;

    /** E.g. {@code Shipment}, {@code Manifest}, {@code Wallet} — the URL's first segment, titled. */
    @Column(name = "module", length = 100)
    private String module;

    @Column(name = "submodule", length = 100)
    private String submodule;

    /** E.g. {@code CREATE}, {@code UPDATE}, {@code DISPATCH}, {@code STATUS_CHANGE}. */
    @Column(name = "action", length = 60, nullable = false)
    private String action;

    /** Simple class name of the affected aggregate, when resolvable. */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** Free-form: most entities key on a UUID, some (AWB numbers) do not. */
    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "description", length = 1000)
    private String description;

    /** JSON. Masked of secrets/tokens/passwords before storage — see {@code SensitiveDataMasker}. */
    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device", length = 30)
    private String device;

    @Column(name = "browser", length = 60)
    private String browser;

    @Column(name = "os", length = 60)
    private String os;

    /** The {@code user_sessions} row id for the request's bearer token, when resolvable. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_id", columnDefinition = "BINARY(16)")
    private UUID sessionId;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "api_endpoint", length = 255)
    private String apiEndpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ActivityStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
