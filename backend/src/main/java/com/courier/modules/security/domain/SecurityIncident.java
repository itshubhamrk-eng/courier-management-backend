package com.courier.modules.security.domain;

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
 * One detected security incident and the decision taken on it.
 *
 * <p>Extends {@link BaseEntity}, not {@code CompanyOwnedEntity}, for the same reason
 * {@code ActivityLog} does: {@code companyId} (and {@code userId}) must stay nullable for
 * incidents that cannot be tied to a tenant, which a Hibernate company filter would
 * fight. Company isolation for reads is enforced explicitly in the service layer.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "security_incident", indexes = {
        @Index(name = "idx_security_incident_company_time", columnList = "company_id, detected_at"),
        @Index(name = "idx_security_incident_user_rule_status", columnList = "user_id, rule_code, status")
})
public class SecurityIncident extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", length = 40, nullable = false)
    private IncidentRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10, nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private IncidentStatus status;

    /** JSON string: the facts the rule saw (IPs, agents, counts). */
    @Column(name = "evidence", columnDefinition = "JSON")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_action", length = 20, nullable = false)
    private AutoAction autoAction;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;
}
