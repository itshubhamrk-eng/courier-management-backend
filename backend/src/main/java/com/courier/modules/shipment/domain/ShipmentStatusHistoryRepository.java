package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A shipment's status timeline, oldest first. */
public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, UUID> {

    @Query("select h from ShipmentStatusHistory h where h.shipmentId = :shipmentId "
            + "and h.companyId = :companyId order by h.changedAt asc")
    List<ShipmentStatusHistory> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                                 @Param("companyId") UUID companyId);

    /** Batch "when was each of these shipments last IN_SCAN'd" — the Bulk Shipment Tracking
     *  report's Received Date column, same "one query, not one per row" shape as
     *  {@code ShipmentServiceImpl.deliveredAtFor}. A shipment crossing more than one hop can
     *  have several IN_SCAN entries; the caller picks the latest by {@code changedAt}. */
    @Query("select h from ShipmentStatusHistory h where h.companyId = :companyId "
            + "and h.shipmentId in :shipmentIds and h.status = :status")
    List<ShipmentStatusHistory> findAllByCompanyIdAndShipmentIdInAndStatus(
            @Param("companyId") UUID companyId, @Param("shipmentIds") Collection<UUID> shipmentIds,
            @Param("status") ShipmentStatus status);

    // -------------------------------------------------------------- dashboard: recent activity
    // Same explicit-companyId discipline as ShipmentRepository (see its own javadoc and
    // DashboardServiceImpl.summary() — ISSUE-001): summary() is deliberately not
    // @Transactional, so an implicit Hibernate companyFilter can't be relied on here either.

    /** Unfiltered on purpose — only safe inside a CompanyContext.runAs(null, ...) block. */
    List<ShipmentStatusHistory> findTop5ByStatusOrderByChangedAtDesc(ShipmentStatus status);

    List<ShipmentStatusHistory> findTop5ByCompanyIdAndStatusOrderByChangedAtDesc(
            UUID companyId, ShipmentStatus status);

    /** Branch-scoped sibling for a caller with an own branch (BRANCH_MANAGER/
     *  BRANCH_OPERATOR) — the company-scoped method above leaked whole-company delivery
     *  activity to a branch-scoped caller's Recent Activity feed. */
    List<ShipmentStatusHistory> findTop5ByCompanyIdAndBranchIdAndStatusOrderByChangedAtDesc(
            UUID companyId, UUID branchId, ShipmentStatus status);
}
