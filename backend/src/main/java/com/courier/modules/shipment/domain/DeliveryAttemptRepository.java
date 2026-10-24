package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    @Query("select a from DeliveryAttempt a where a.companyId = :companyId and a.shipmentId = :shipmentId "
            + "order by a.attemptNo asc")
    List<DeliveryAttempt> findByShipment(@Param("companyId") UUID companyId, @Param("shipmentId") UUID shipmentId);

    long countByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);
}
