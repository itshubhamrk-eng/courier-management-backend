package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    @Query("select d from DeliveryAssignment d where d.shipmentId = :shipmentId and d.companyId = :companyId")
    Optional<DeliveryAssignment> findByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                                @Param("companyId") UUID companyId);

    /** Batch load for a page of shipment rows — the Delivery Report's "delivered at" column. */
    List<DeliveryAssignment> findByShipmentIdIn(Collection<UUID> shipmentIds);

    /** Delivery Report's date-range filter — resolved to a shipment id set up front since
     *  {@code deliveredAt} lives on this entity, not on {@code Shipment}. */
    @Query("select d.shipmentId from DeliveryAssignment d where d.deliveredAt >= :from and d.deliveredAt < :to")
    List<UUID> findShipmentIdsDeliveredBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** DRS Report's own grouping source — see {@code ShipmentService.listDrs}/{@code getDrsDetail}.
     *  Grouped by (deliveryUserId, deliveryBranchId, day) in the service layer rather than SQL,
     *  since there is no DRS batch id to group on and this keeps the query trivial. */
    @Query("select d from DeliveryAssignment d where d.companyId = :companyId "
            + "and d.assignedAt >= :from and d.assignedAt < :to order by d.assignedAt desc")
    List<DeliveryAssignment> findByCompanyAndAssignedAtBetween(@Param("companyId") UUID companyId,
                                                                @Param("from") Instant from,
                                                                @Param("to") Instant to);
}
