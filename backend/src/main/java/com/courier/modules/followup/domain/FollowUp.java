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

/**
 * One operational follow-up task — a branch user's reminder to take manual action on a
 * shipment, customer, delivery, payment or exception. {@link #status} moves only through
 * {@link FollowUpStatus#canTransitionTo}; {@code FollowUpServiceImpl}'s transition guard
 * is the one place that is enforced — this entity is a plain record of the current state.
 *
 * <p>{@link #branchId} is mandatory, unlike Ticket Support's optional
 * {@code relatedBranchId} — a follow-up is always somebody's branch responsibility.
 * {@link #referenceId}/{@link #customerId}/{@link #shipmentId} carry no physical FK —
 * cross-module references, the same convention {@code Ticket.relatedShipmentId} and
 * {@code shipments.booking_branch_id} already use.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "follow_up",
        indexes = {
                @Index(name = "idx_follow_up_company_branch", columnList = "company_id, branch_id"),
                @Index(name = "idx_follow_up_company_status", columnList = "company_id, status"),
                @Index(name = "idx_follow_up_company_assignee", columnList = "company_id, assigned_user_id"),
                @Index(name = "idx_follow_up_company_due", columnList = "company_id, due_date"),
                @Index(name = "idx_follow_up_shipment", columnList = "shipment_id"),
                @Index(name = "idx_follow_up_customer", columnList = "customer_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class FollowUp extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", nullable = false, length = 20)
    private FollowUpType referenceType;

    /** No physical FK — which table this points at is {@link #referenceType}; null when
     *  the follow-up isn't about one specific other record (e.g. GENERAL). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    private UUID referenceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)")
    private UUID shipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assigned_user_id", columnDefinition = "BINARY(16)")
    private UUID assignedUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "follow_up_type", nullable = false, length = 20)
    private FollowUpType followUpType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", nullable = false, length = 20)
    private FollowUpPriority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private FollowUpStatus status;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    /** Set by {@code reschedule} (the new target date) and left null otherwise — a
     *  follow-up that has never been rescheduled has no "next" date distinct from
     *  {@link #dueDate}. */
    @Column(name = "next_follow_up_date")
    private Instant nextFollowUpDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "completed_by", columnDefinition = "BINARY(16)")
    private UUID completedBy;

    /** Idempotency flags for {@code FollowUpSweepJob} — each notification fires once per
     *  due date. Reset by {@link #resetSweepFlags()} whenever the due date moves. */
    @Column(name = "overdue_notified", nullable = false)
    private boolean overdueNotified;

    @Column(name = "due_today_notified", nullable = false)
    private boolean dueTodayNotified;

    public boolean isAssignee(UUID userId) {
        return userId != null && userId.equals(assignedUserId);
    }

    public void resetSweepFlags() {
        this.overdueNotified = false;
        this.dueTodayNotified = false;
    }
}
