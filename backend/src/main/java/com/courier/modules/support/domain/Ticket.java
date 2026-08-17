package com.courier.modules.support.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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

import java.time.Instant;
import java.util.UUID;

/**
 * A support ticket. {@link #status} moves only through {@link TicketStatus#canTransitionTo};
 * {@code TicketServiceImpl}'s transition guard is the one place that is enforced — this
 * entity is a plain record of the current state.
 *
 * <p>{@code relatedShipmentId}/{@code relatedCustomerId}/{@code relatedBranchId} carry no
 * physical FK — cross-module references, the same convention {@code Shipment
 * .bookingBranchId} and {@code crossing_details} already use, so this module never
 * depends on shipment/customer/company entities directly.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_tickets_company_number",
                columnNames = {"company_id", "ticket_number"}),
        indexes = {
                @Index(name = "idx_tickets_company_status", columnList = "company_id, status"),
                @Index(name = "idx_tickets_company_assignee", columnList = "company_id, assignee_user_id"),
                @Index(name = "idx_tickets_company_created_by", columnList = "company_id, created_by_user_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Ticket extends CompanyOwnedEntity {

    @Column(name = "ticket_number", nullable = false, updatable = false, length = 20)
    private String ticketNumber;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "category_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID categoryId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "sub_category_id", columnDefinition = "BINARY(16)")
    private UUID subCategoryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private TicketStatus status;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "related_shipment_id", columnDefinition = "BINARY(16)")
    private UUID relatedShipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "related_customer_id", columnDefinition = "BINARY(16)")
    private UUID relatedCustomerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "related_branch_id", columnDefinition = "BINARY(16)")
    private UUID relatedBranchId;

    /** Null for a system-raised ticket (e.g. {@code ShipmentSlaSweepJob}) — every
     *  human-raised ticket has one. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "created_by_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID createdByUserId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assignee_user_id", columnDefinition = "BINARY(16)")
    private UUID assigneeUserId;

    @Column(name = "escalated", nullable = false)
    private boolean escalated;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    /** Null when no active {@link TicketSlaRule} matched this ticket's priority at
     *  creation — SLA is opt-in per company, not every ticket carries one. */
    @Column(name = "sla_first_response_due_at")
    private Instant slaFirstResponseDueAt;

    @Column(name = "sla_resolution_due_at")
    private Instant slaResolutionDueAt;

    /** Idempotency flags for {@code TicketSlaSweepJob} — a warning/breach fires once. */
    @Column(name = "sla_warning_notified", nullable = false)
    private boolean slaWarningNotified;

    @Column(name = "sla_breach_notified", nullable = false)
    private boolean slaBreachNotified;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public boolean isRequester(UUID userId) {
        return userId != null && userId.equals(createdByUserId);
    }

    public boolean isAssignee(UUID userId) {
        return userId != null && userId.equals(assigneeUserId);
    }
}
