package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** A shipment's uploaded images (booking photo, POD photo/signature), newest first. */
public interface ShipmentAssetRepository extends JpaRepository<ShipmentAsset, UUID> {

    @Query("select a from ShipmentAsset a where a.shipmentId = :shipmentId "
            + "and a.companyId = :companyId order by a.createdAt desc")
    List<ShipmentAsset> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                         @Param("companyId") UUID companyId);
}
