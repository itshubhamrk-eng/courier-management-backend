package com.courier.modules.busdepot.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentBusCrossingRepository extends JpaRepository<ShipmentBusCrossing, UUID> {

    @Query("select c from ShipmentBusCrossing c where c.shipmentId = :shipmentId and c.companyId = :companyId")
    Optional<ShipmentBusCrossing> findByShipment(@Param("shipmentId") UUID shipmentId, @Param("companyId") UUID companyId);
}
