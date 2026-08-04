package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** A shipment's status timeline, oldest first. */
public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, UUID> {

    @Query("select h from ShipmentStatusHistory h where h.shipmentId = :shipmentId "
            + "and h.companyId = :companyId order by h.changedAt asc")
    List<ShipmentStatusHistory> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                                 @Param("companyId") UUID companyId);
}
