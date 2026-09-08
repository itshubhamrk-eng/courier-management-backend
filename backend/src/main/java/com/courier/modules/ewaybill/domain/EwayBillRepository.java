package com.courier.modules.ewaybill.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EwayBillRepository extends JpaRepository<EwayBill, UUID> {

    @Query("select e from EwayBill e where e.id = :id and e.companyId = :companyId")
    Optional<EwayBill> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /** Newest first — the first row is "the current one" for a shipment that has been
     *  re-issued after a cancellation. */
    @Query("select e from EwayBill e where e.shipmentId = :shipmentId and e.companyId = :companyId "
            + "order by e.createdAt desc")
    List<EwayBill> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                     @Param("companyId") UUID companyId);

    /** Batch form of {@link #findAllByShipmentIdWithinCompany} — one query for a whole
     *  page of shipments (e.g. a THC's shipment list) rather than one per row. Newest
     *  first per shipment, same as the single-id form; the caller picks the first
     *  non-cancelled (or simply first) row per {@code shipmentId}. */
    @Query("select e from EwayBill e where e.shipmentId in :shipmentIds and e.companyId = :companyId "
            + "order by e.createdAt desc")
    List<EwayBill> findAllByShipmentIdInWithinCompany(@Param("shipmentIds") Collection<UUID> shipmentIds,
                                                       @Param("companyId") UUID companyId);

    @Query("select e from EwayBill e where e.companyId = :companyId "
            + "and (:shipmentId is null or e.shipmentId = :shipmentId) "
            + "and (:status is null or e.status = :status) "
            + "order by e.createdAt desc")
    Page<EwayBill> search(@Param("companyId") UUID companyId, @Param("shipmentId") UUID shipmentId,
                          @Param("status") EwayBillStatus status, Pageable pageable);
}
