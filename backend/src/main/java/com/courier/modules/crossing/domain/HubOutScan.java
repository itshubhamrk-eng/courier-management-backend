package com.courier.modules.crossing.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One shipment, confirmed physically present at a hub before its Load Sheet may
 * dispatch. The {@code (company_id, manifest_id, shipment_id)} unique constraint on the
 * table is the duplicate-scan guard — structural, not a service-layer check, the same
 * style {@code DeliveryAssignment}'s {@code uk_delivery_assignment_company_shipment}
 * already uses.
 *
 * <p>No physical FK to {@code manifests}/{@code shipments}/{@code branches} — every one
 * belongs to a different module, validated in {@code HubOperationsServiceImpl}.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "hub_out_scans",
        uniqueConstraints = @UniqueConstraint(name = "uk_hub_out_scans_manifest_shipment",
                columnNames = {"company_id", "manifest_id", "shipment_id"}),
        indexes = @Index(name = "idx_hub_out_scans_hub", columnList = "company_id, hub_branch_id, scanned_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class HubOutScan extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "manifest_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID manifestId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hub_branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID hubBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "scanned_by", columnDefinition = "BINARY(16)", updatable = false)
    private UUID scannedBy;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;
}
