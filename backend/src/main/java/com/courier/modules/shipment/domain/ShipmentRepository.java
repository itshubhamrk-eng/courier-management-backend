package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
