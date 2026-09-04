package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A shipment's uploaded images (booking photo, POD photo/signature), newest first. */
public interface ShipmentAssetRepository extends JpaRepository<ShipmentAsset, UUID> {

    @Query("select a from ShipmentAsset a where a.shipmentId = :shipmentId "
            + "and a.companyId = :companyId order by a.createdAt desc")
    List<ShipmentAsset> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                         @Param("companyId") UUID companyId);

    /** Batch form of {@link #findAllByShipmentIdWithinCompany}, for a paginated table that
     *  needs one POD photo per row without an N+1 query per shipment. */
    @Query("select a from ShipmentAsset a where a.shipmentId in :shipmentIds "
            + "and a.assetType = :assetType and a.companyId = :companyId order by a.createdAt desc")
    List<ShipmentAsset> findByShipmentIdInAndAssetTypeWithinCompany(
            @Param("shipmentIds") Collection<UUID> shipmentIds,
            @Param("assetType") ShipmentAssetType assetType, @Param("companyId") UUID companyId);
}
