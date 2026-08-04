package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** A shipment's uploaded document references, newest first. */
public interface ShipmentDocumentRepository extends JpaRepository<ShipmentDocument, UUID> {

    @Query("select d from ShipmentDocument d where d.shipmentId = :shipmentId "
            + "and d.companyId = :companyId order by d.createdAt desc")
    List<ShipmentDocument> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                            @Param("companyId") UUID companyId);
}
