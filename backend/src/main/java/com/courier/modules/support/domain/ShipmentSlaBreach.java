package com.courier.modules.support.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row per shipment per {@link ShipmentSlaStage} ever breached — the idempotency
 * record for {@code ShipmentSlaSweepJob}, not a ledger: a shipment normally passes
 * through each stage once, so once a ticket is raised for (shipment, stage) it is never
 * raised again, even if the shipment later re-enters the same status.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shipment_sla_breaches",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipment_sla_breaches_shipment_stage",
                columnNames = {"company_id", "shipment_id", "stage"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentSlaBreach extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "stage", nullable = false, length = 40, updatable = false)
    private ShipmentSlaStage stage;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ticket_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "hours_elapsed", nullable = false, updatable = false)
    private int hoursElapsed;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;
}
