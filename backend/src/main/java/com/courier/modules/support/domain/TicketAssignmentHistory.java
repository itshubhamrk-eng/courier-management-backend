package com.courier.modules.support.domain;

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

import java.util.UUID;

/** One immutable row per assign/reassign/escalate action — never edited, only added. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_assignment_history",
        indexes = @Index(name = "idx_ticket_assignment_history_ticket", columnList = "company_id, ticket_id, created_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class TicketAssignmentHistory extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ticketId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assigned_to_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID assignedToUserId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assigned_by_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID assignedByUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, length = 20)
    private TicketAssignmentAction action;

    @Column(name = "remarks", length = 1000)
    private String remarks;
}
