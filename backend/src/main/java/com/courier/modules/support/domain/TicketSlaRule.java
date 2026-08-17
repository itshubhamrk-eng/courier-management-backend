package com.courier.modules.support.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * A company's SLA target for one priority tier — one row per {@link TicketPriority},
 * not the full category x priority x tenant x support-team matrix the spec describes.
 * "Support team" has no concept in this codebase (Phase 1's own scoping decision) and
 * "tenant" is already this row's own {@code companyId}; priority is the dimension that
 * actually drives SLA in practice. A per-category override is a documented gap.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_sla_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_sla_rules_company_priority",
                columnNames = {"company_id", "priority"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class TicketSlaRule extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", nullable = false, length = 20, updatable = false)
    private TicketPriority priority;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "active", nullable = false)
    private boolean active;
}
