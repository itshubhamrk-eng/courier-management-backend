package com.courier.modules.crossing.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
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
 * An operational exception raised against a shipment at a hub — Missing, Damaged, Short,
 * Wrong Destination, Misrouted or On Hold.
 *
 * <p><b>Never mutates {@code Shipment.status}.</b> A shipment stuck at a hub because
 * something is wrong with it is not thereby delivered, cancelled or returned — those
 * remain the shipment's own state machine's decisions, made deliberately by whoever
 * resolves the exception, not automatically by raising one. This entity is a parallel
 * incident record, the same relationship {@code PodVerification} has to {@code Shipment}
 * (its own append-only row, reviewed and closed by a human, never itself moving the
 * shipment).
 *
 * <p>No physical FK to {@code shipments} or {@code branches} — both belong to different
 * modules, validated in {@code HubOperationsServiceImpl}, the same cross-module treatment
 * every other id on a {@code CompanyOwnedEntity} table gets.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shipment_exceptions",
        indexes = {
                @Index(name = "idx_shipment_exceptions_shipment", columnList = "company_id, shipment_id, status"),
                @Index(name = "idx_shipment_exceptions_hub",
                        columnList = "company_id, hub_branch_id, status, raised_at")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentException extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hub_branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID hubBranchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 30, updatable = false)
    private ShipmentExceptionType exceptionType;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ShipmentExceptionStatus status = ShipmentExceptionStatus.OPEN;

    @Column(name = "remarks", length = 500, updatable = false)
    private String remarks;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "raised_by", columnDefinition = "BINARY(16)", updatable = false)
    private UUID raisedBy;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    @Setter
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;

    @Setter
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Setter
    @Column(name = "resolution_remarks", length = 500)
    private String resolutionRemarks;

    /** The best-effort support ticket this exception raised, if one was created —
     *  null when the "Shipment Issue" category was missing/inactive at the time. */
    @Setter
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)")
    private UUID ticketId;

    public boolean isOpen() {
        return status == ShipmentExceptionStatus.OPEN;
    }

    /**
     * @throws BusinessRuleException already resolved — an exception is closed once, the
     *         same one-way lifecycle {@code CrossingDetail.isTerminal()} enforces
     */
    public void resolve(UUID resolvedByUserId, String resolutionRemarks) {
        if (!isOpen()) {
            throw new BusinessRuleException("This exception has already been resolved.");
        }
        this.status = ShipmentExceptionStatus.RESOLVED;
        this.resolvedBy = resolvedByUserId;
        this.resolvedAt = Instant.now();
        this.resolutionRemarks = resolutionRemarks;
    }
}
