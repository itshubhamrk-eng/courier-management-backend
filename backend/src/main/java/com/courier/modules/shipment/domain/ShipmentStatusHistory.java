package com.courier.modules.shipment.domain;

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
 * One entry of a shipment's status timeline. Append-only: a service never updates or
 * removes a row here, the same discipline {@code finance.domain.WalletTransaction} applies
 * to the ledger — a tracking history that can be rewritten is not evidence.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipment_status_history",
        indexes = @Index(name = "idx_shipment_status_history_shipment",
                columnList = "company_id, shipment_id, changed_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentStatusHistory extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    /** Which branch this transition happened at — null for BOOKED/CANCELLED (Shipment Booking never set it). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID branchId;

    /** Which manifest this transition belongs to — set from MANIFEST_CREATED onward. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "manifest_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID manifestId;

    /** Which vehicle carried it — set only on the DISPATCHED entry. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "vehicle_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private ShipmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private ShipmentStatus previousStatus;

    @Column(name = "remarks", length = 500, updatable = false)
    private String remarks;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "changed_by", columnDefinition = "BINARY(16)", updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
