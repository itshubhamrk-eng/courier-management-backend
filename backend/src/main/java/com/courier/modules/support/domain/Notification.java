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

/** One in-app notification for one recipient. Generated synchronously by
 *  {@code TicketServiceImpl} at the point of each action, plus a scheduled sweep
 *  ({@code TicketSlaSweepJob}) for the two SLA events. Never edited, only read/marked
 *  read — same "never edit, only add" shape the ledger and status history follow. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient", columnList = "company_id, recipient_user_id, created_at"),
                @Index(name = "idx_notifications_recipient_unread", columnList = "company_id, recipient_user_id, is_read")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Notification extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "recipient_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 500, updatable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID ticketId;

    @Column(name = "is_read", nullable = false)
    private boolean read;
}
