package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    @Query("select d from DeliveryAssignment d where d.shipmentId = :shipmentId and d.companyId = :companyId")
    Optional<DeliveryAssignment> findByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                                @Param("companyId") UUID companyId);
}
