package com.courier.modules.support.domain;

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

import java.util.UUID;

/**
 * One entry in a ticket's conversation thread — a public reply or a staff-only internal
 * note, distinguished by {@link #isInternalNote}. Internal notes are stripped from the
 * response before they ever reach a non-staff caller — enforced in
 * {@code TicketServiceImpl}/{@code TicketMapper}, never left to the frontend to hide.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_messages",
        indexes = @Index(name = "idx_ticket_messages_ticket", columnList = "company_id, ticket_id, created_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class TicketMessage extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ticketId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "author_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_internal_note", nullable = false)
    private boolean internalNote;
}
