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

/**
 * One immutable row per status transition — never edited, only added, same rule the
 * wallet ledger follows. {@code fromStatus} is null on the row created at ticket creation.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_status_history",
        indexes = @Index(name = "idx_ticket_status_history_ticket", columnList = "company_id, ticket_id, created_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class TicketStatusHistory extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 30)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 30)
    private TicketStatus toStatus;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "changed_by_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID changedByUserId;

    @Column(name = "remarks", length = 1000)
    private String remarks;
}
