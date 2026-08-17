package com.courier.modules.crossing.domain;

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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One hop of a shipment's crossing through intermediate branches/hubs on its way to the
 * delivery branch — a journey may cross through several in sequence
 * ({@link #sequenceOrder}, 0-based), one row per hop. Each hop's own status/charge is its
 * current state, not a ledger, the same split {@code DeliveryAssignment} already draws
 * for delivery.
 *
 * <p>No physical FK to {@code shipments} or {@code branches}: both belong to different
 * modules (shipment, company), validated in {@code CrossingServiceImpl} — the same
 * cross-module treatment every other id on a {@code CompanyOwnedEntity} table gets.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "crossing_details",
        uniqueConstraints = @UniqueConstraint(name = "uk_crossing_details_company_shipment_seq",
                columnNames = {"company_id", "shipment_id", "sequence_order"}),
        indexes = @Index(name = "idx_crossing_details_branch_status",
                columnList = "company_id, branch_id, status"))
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CrossingDetail extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    /** 0-based position of this hop in the shipment's crossing route — hop 0 is the first
     *  branch it reaches after leaving the booking branch. */
    @Column(name = "sequence_order", nullable = false, updatable = false)
    private int sequenceOrder;

    @Setter
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID branchId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CrossingStatus status = CrossingStatus.PENDING;

    @Setter
    @Column(name = "charge", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal charge = BigDecimal.ZERO;

    public boolean isTerminal() {
        return status == CrossingStatus.COMPLETED || status == CrossingStatus.CANCELLED;
    }
}
