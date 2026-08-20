package com.courier.modules.shipment.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shipments, within a company.
 *
 * <p>Single-row loads go through {@link #findByIdWithinCompany} — a primary-key load
 * bypasses the Hibernate filter, the same reasoning every other module's repository
 * documents. Neither {@code shipmentNumber} nor {@code trackingNumber} needs an
 * existence check before insert any more — both are counters
 * ({@code BranchShipmentSequenceRepository} / {@code CompanyShipmentSequenceRepository})
 * that can't collide, unlike {@code finance.domain.WalletRepository}'s own generated,
 * dated-random numbers.
 */
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>,
        JpaSpecificationExecutor<Shipment> {

    @Query("select s from Shipment s where s.id = :id and s.companyId = :companyId")
    Optional<Shipment> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<Shipment> findByCompanyIdAndTrackingNumber(UUID companyId, String trackingNumber);

    boolean existsByCompanyIdAndShipmentNumber(UUID companyId, String shipmentNumber);

    List<Shipment> findAllByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);

    List<Shipment> findAllByCompanyIdAndManifestIdIn(UUID companyId, Collection<UUID> manifestIds);

    // -------------------------------------------------------------- cross-tenant
    // Unfiltered on purpose — only ever safe to call from inside a
    // CompanyContext.runAs(null, ...) block (a genuinely cross-tenant platform-level
    // read), never from a company-bound caller. See DashboardServiceImpl.summary().

    long countByBookingDateBetween(LocalDate start, LocalDate end);

    long countByStatus(ShipmentStatus status);

    long countByStatusIn(Collection<ShipmentStatus> statuses);

    long countByStatusAndBookingDateBetween(ShipmentStatus status, LocalDate start, LocalDate end);

    long countByStatusInAndBookingDateBetween(Collection<ShipmentStatus> statuses, LocalDate start, LocalDate end);

    long countByDeliveryBranchIdAndStatusIn(UUID deliveryBranchId, Collection<ShipmentStatus> statuses);

    List<Shipment> findTop5ByOrderByCreatedAtDesc();

    /** Backs the platform-wide "Shipment Trend" chart — one row per day that had at
     *  least one booking; days with none simply don't appear (DashboardServiceImpl
     *  fills the gaps with zero). */
    @Query("select s.bookingDate as day, count(s) as count from Shipment s "
            + "where s.bookingDate between :start and :end group by s.bookingDate")
    List<DailyCountRow> countDailyByBookingDateBetween(@Param("start") LocalDate start,
                                                        @Param("end") LocalDate end);

    // -------------------------------------------------------------- company-scoped
    // Explicit companyId predicate, not left to the implicit Hibernate companyFilter —
    // DashboardServiceImpl.summary() is deliberately not @Transactional (see its own
    // javadoc), so each of these runs in its own short-lived session/transaction; an
    // explicit predicate is correct regardless of session boundaries, where relying on
    // CompanyFilterAspect having enabled the filter on that specific session is not
    // (see MEMORY: dashboard-cross-tenant-leak — this is ISSUE-001's actual fix).

    long countByCompanyIdAndBookingDateBetween(UUID companyId, LocalDate start, LocalDate end);

    long countByCompanyIdAndStatus(UUID companyId, ShipmentStatus status);

    long countByCompanyIdAndStatusIn(UUID companyId, Collection<ShipmentStatus> statuses);

    long countByCompanyIdAndStatusAndBookingDateBetween(UUID companyId, ShipmentStatus status,
                                                         LocalDate start, LocalDate end);

    long countByCompanyIdAndStatusInAndBookingDateBetween(UUID companyId, Collection<ShipmentStatus> statuses,
                                                           LocalDate start, LocalDate end);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndDeliveryBranchIdAndStatusIn(UUID companyId, UUID deliveryBranchId,
                                                         Collection<ShipmentStatus> statuses);

    List<Shipment> findTop5ByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** Backs the Company Overview "Delayed Shipments" action-required tile: booked more
     *  than a few days ago and not yet in a terminal state. Not SLA-rule-aware (see
     *  {@code ShipmentSlaSweepService} for the real per-stage thresholds) — a simple,
     *  company-wide backlog heuristic. */
    long countByCompanyIdAndStatusNotInAndBookingDateBefore(UUID companyId,
                                                              Collection<ShipmentStatus> excludedStatuses,
                                                              LocalDate cutoff);

    /** Backs the "Shipment Trend" chart for a company-bound caller — see the unscoped
     *  sibling {@link #countDailyByBookingDateBetween} for the cross-tenant case. */
    @Query("select s.bookingDate as day, count(s) as count from Shipment s "
            + "where s.companyId = :companyId and s.bookingDate between :start and :end "
            + "group by s.bookingDate")
    List<DailyCountRow> countDailyByCompanyIdAndBookingDateBetween(@Param("companyId") UUID companyId,
                                                                    @Param("start") LocalDate start,
                                                                    @Param("end") LocalDate end);

    /** Backs the "Delivery Performance" chart: how many of each day's bookings have
     *  reached each status bucket as of now. Company-scoped only — no profile shows
     *  this chart cross-tenant (see dashboard.roles.ts PLATFORM: deliveryPerformance
     *  false), so no unscoped sibling exists. */
    @Query("select s.bookingDate as day, "
            + "sum(case when s.status = :delivered then 1L else 0L end) as delivered, "
            + "sum(case when s.status in :inTransit then 1L else 0L end) as inTransit, "
            + "sum(case when s.status in :pending then 1L else 0L end) as pending "
            + "from Shipment s where s.companyId = :companyId and s.bookingDate between :start and :end "
            + "group by s.bookingDate")
    List<DailyDeliveryPerformanceRow> dailyDeliveryPerformanceByCompanyIdAndBookingDateBetween(
            @Param("companyId") UUID companyId, @Param("delivered") ShipmentStatus delivered,
            @Param("inTransit") Collection<ShipmentStatus> inTransit,
            @Param("pending") Collection<ShipmentStatus> pending,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Projection for both daily-count trend queries above. */
    interface DailyCountRow {
        LocalDate getDay();
        long getCount();
    }

    /** Projection for {@link #dailyDeliveryPerformanceByCompanyIdAndBookingDateBetween}. */
    interface DailyDeliveryPerformanceRow {
        LocalDate getDay();
        long getDelivered();
        long getInTransit();
        long getPending();
    }

    // -------------------------------------------------------------- branch-scoped
    // Backs the Branch Overview pipeline/action-required card (DashboardServiceImpl,
    // BRANCH_MANAGER/BRANCH_OPERATOR/hub callers — anyone with an own branch wallet).
    // Filtered on currentLocationId, not bookingBranchId/deliveryBranchId: that column is
    // "which branch physically holds this shipment right now" (set to the booking branch
    // at creation, advanced by CrossingService.arriveAt on every hub hop — see
    // MEMORY/AI_CONTEXT.md 0.26.0/0.27.0), the one field that's correct for every pipeline
    // stage including a shipment mid-crossing through a branch that is neither its
    // booking nor delivery branch.

    long countByCompanyIdAndCurrentLocationIdAndStatusAndBookingDateBetween(
            UUID companyId, UUID currentLocationId, ShipmentStatus status, LocalDate start, LocalDate end);

    long countByCompanyIdAndCurrentLocationIdAndStatusIn(
            UUID companyId, UUID currentLocationId, Collection<ShipmentStatus> statuses);

    long countByCompanyIdAndCurrentLocationIdAndStatusNotInAndBookingDateBefore(
            UUID companyId, UUID currentLocationId, Collection<ShipmentStatus> excludedStatuses, LocalDate cutoff);

    /** Backs the Company Overview "Top Routes" card: one row per destination branch,
     *  ranked by month-to-date shipment count. Native — {@code Shipment}/{@code
     *  ShipmentCharge} have no ORM association (see {@code ShipmentChargeRepository}'s own
     *  class doc), so the revenue join has to be written as SQL. {@code Pageable} supplies
     *  the "top N" limit; the query itself carries no {@code LIMIT} clause. */
    @Query(value = """
            SELECT s.delivery_branch_id AS branchId, COUNT(*) AS shipmentCount,
                   COALESCE(SUM(c.net_amount), 0) AS revenue
            FROM shipments s
            LEFT JOIN shipment_charges c ON c.shipment_id = s.id AND c.company_id = :companyId
            WHERE s.company_id = :companyId AND s.deleted = FALSE
              AND s.booking_date BETWEEN :start AND :end
            GROUP BY s.delivery_branch_id
            ORDER BY shipmentCount DESC
            """, nativeQuery = true)
    List<TopRouteRow> findTopRoutesByCompanyIdAndBookingDateBetween(
            @Param("companyId") UUID companyId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, Pageable pageable);

    /** Native-query projection for {@link #findTopRoutesByCompanyIdAndBookingDateBetween}.
     *  {@code branchId} comes back as the column's raw {@code BINARY(16)} bytes, not
     *  {@code UUID} — Spring Data has no byte[]-to-UUID converter for native-query
     *  projections (unlike entity mapping, which Hibernate itself handles), so a
     *  {@code UUID}-typed accessor here throws {@code UnsupportedOperationException} the
     *  moment it's called. Convert with {@code TimeOrderedUuid.fromBytes} at the call
     *  site instead. */
    interface TopRouteRow {
        byte[] getBranchId();
        long getShipmentCount();
        BigDecimal getRevenue();
    }

    /** Backs the Company Overview "Top Customers" card: grouped by {@code sender_contact}
     *  — see {@link com.courier.modules.dashboard.api.dto.TopCustomerResponse}'s own doc
     *  for why there is no {@code Customer} id to group by instead. */
    @Query(value = """
            SELECT s.sender_contact AS contact, MAX(s.sender_name) AS name,
                   COUNT(*) AS shipmentCount, COALESCE(SUM(c.net_amount), 0) AS revenue
            FROM shipments s
            LEFT JOIN shipment_charges c ON c.shipment_id = s.id AND c.company_id = :companyId
            WHERE s.company_id = :companyId AND s.deleted = FALSE
              AND s.booking_date BETWEEN :start AND :end AND s.sender_contact IS NOT NULL
            GROUP BY s.sender_contact
            ORDER BY shipmentCount DESC
            """, nativeQuery = true)
    List<TopCustomerRow> findTopCustomersByCompanyIdAndBookingDateBetween(
            @Param("companyId") UUID companyId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, Pageable pageable);

    /** Native-query projection for {@link #findTopCustomersByCompanyIdAndBookingDateBetween}. */
    interface TopCustomerRow {
        String getContact();
        String getName();
        long getShipmentCount();
        BigDecimal getRevenue();
    }

    /** One row per shipment currently sitting past its stage's SLA threshold — backs
     *  {@code shipment.infrastructure.ShipmentSlaAdapter}. Native: the "how long has this
     *  shipment been in its current status" comparison is a per-row {@code TIMESTAMPDIFF}
     *  against the latest {@code shipment_status_history} row, awkward to express in JPQL
     *  and only meaningful as SQL. Filtering by {@code company_id} explicitly here is
     *  required, not optional — a native query bypasses the Hibernate {@code companyFilter}
     *  the same way every other native query in this codebase documents. */
    @Query(value = """
            SELECT s.id AS shipmentId, s.tracking_number AS trackingNumber,
                   s.current_location_id AS branchId, s.status AS status,
                   h.changed_at AS enteredAt
            FROM shipments s
            JOIN (
                SELECT shipment_id, MAX(changed_at) AS changed_at
                FROM shipment_status_history
                WHERE company_id = :companyId
                GROUP BY shipment_id
            ) h ON h.shipment_id = s.id
            WHERE s.company_id = :companyId
              AND s.deleted = FALSE
              AND (
                   (s.status = 'BOOKED' AND TIMESTAMPDIFF(HOUR, h.changed_at, :asOf) >= :bookingHours)
                OR (s.status = 'MANIFEST_CREATED' AND TIMESTAMPDIFF(HOUR, h.changed_at, :asOf) >= :loadingSheetHours)
                OR (s.status = 'DISPATCHED' AND TIMESTAMPDIFF(HOUR, h.changed_at, :asOf) >= :thcHours)
                OR (s.status = 'IN_SCAN' AND TIMESTAMPDIFF(HOUR, h.changed_at, :asOf) >= :inscanHours)
                OR (s.status = 'OUT_FOR_DELIVERY' AND TIMESTAMPDIFF(HOUR, h.changed_at, :asOf) >= :drsHours)
              )
            """, nativeQuery = true)
    List<ShipmentSlaCandidateRow> findSlaBreachCandidates(
            @Param("companyId") UUID companyId,
            @Param("asOf") java.time.Instant asOf,
            @Param("bookingHours") int bookingHours,
            @Param("loadingSheetHours") int loadingSheetHours,
            @Param("thcHours") int thcHours,
            @Param("inscanHours") int inscanHours,
            @Param("drsHours") int drsHours);

    /** Native-query projection for {@link #findSlaBreachCandidates}. */
    interface ShipmentSlaCandidateRow {
        UUID getShipmentId();
        String getTrackingNumber();
        UUID getBranchId();
        String getStatus();
        java.time.Instant getEnteredAt();
    }
}
