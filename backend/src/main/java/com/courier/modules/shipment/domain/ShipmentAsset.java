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

import java.util.UUID;

/**
 * One uploaded image of a shipment — a booking-time photo ({@link ShipmentAssetType#BOOKING})
 * or a delivery-time proof-of-delivery capture ({@link ShipmentAssetType#POD}, {@code kind}
 * {@code PHOTO}/{@code SIGNATURE}). Immutable once written; a re-upload adds a new row rather
 * than replacing one, the newest-per-{@code (assetType, kind)} is what callers read (see
 * {@code ShipmentMapper}'s helper) — the same "current state read from the newest row" shape
 * {@code DeliveryAssignment} itself used to carry directly on two of its own columns before
 * this table existed (V33).
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "shipment_assets",
        indexes = @Index(name = "idx_shipment_assets_shipment",
                columnList = "company_id, shipment_id, asset_type, kind"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentAsset extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20, updatable = false)
    private ShipmentAssetType assetType;

    /** {@code PHOTO} or {@code SIGNATURE} — for {@code BOOKING} always {@code PHOTO}, there is
     *  only one kind of booking-time image. */
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private String kind;

    @Column(name = "asset_url", nullable = false, length = 1000, updatable = false)
    private String assetUrl;
}
