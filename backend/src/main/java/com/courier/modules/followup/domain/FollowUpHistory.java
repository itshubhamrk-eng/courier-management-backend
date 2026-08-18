package com.courier.modules.followup.domain;

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

/** One immutable entry in a follow-up's timeline — created, every status change, every
 *  reschedule, every (re)assignment and every note. Never edited, only appended, same
 *  "never edit, only add" shape as {@code TicketStatusHistory}/{@code
 *  TicketAssignmentHistory} combined into one table (the spec calls for a single
 *  {@code follow_up_history}, not two). */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "follow_up_history",
        indexes = {
                @Index(name = "idx_follow_up_history_follow_up", columnList = "company_id, follow_up_id, created_at")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class FollowUpHistory extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "follow_up_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID followUpId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private FollowUpHistoryAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20, updatable = false)
    private FollowUpStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", length = 20, updatable = false)
    private FollowUpStatus toStatus;

    @Column(name = "previous_due_date", updatable = false)
    private Instant previousDueDate;

    @Column(name = "new_due_date", updatable = false)
    private Instant newDueDate;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assigned_to_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID assignedToUserId;

    @Column(name = "note", length = 1000, updatable = false)
    private String note;

    /** Null for a system-raised entry (e.g. the sweep job's own notifications leave no
     *  history row — only actor-driven changes do). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "changed_by_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID changedByUserId;
}
