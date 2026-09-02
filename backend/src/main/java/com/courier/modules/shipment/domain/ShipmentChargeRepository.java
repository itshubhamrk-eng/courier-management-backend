package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One row per shipment — see {@link ShipmentCharge}. */
public interface ShipmentChargeRepository extends JpaRepository<ShipmentCharge, UUID> {

    @Query("select c from ShipmentCharge c where c.shipmentId = :shipmentId and c.companyId = :companyId")
    Optional<ShipmentCharge> findByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                           @Param("companyId") UUID companyId);

    List<ShipmentCharge> findByShipmentIdIn(Collection<UUID> shipmentIds);

    // Unfiltered on purpose — only safe from inside CompanyContext.runAs(null, ...).
    // See ShipmentRepository's own cross-tenant/company-scoped split and
    // DashboardServiceImpl.summary() (ISSUE-001's fix).

    @Query("select coalesce(sum(c.netAmount), 0) from ShipmentCharge c")
    BigDecimal sumNetAmount();

    @Query("select coalesce(sum(c.netAmount), 0) from ShipmentCharge c "
            + "where c.shipmentId in (select s.id from Shipment s where s.bookingDate between :start and :end)")
    BigDecimal sumNetAmountForBookingDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select coalesce(sum(c.netAmount), 0) from ShipmentCharge c where c.companyId = :companyId")
    BigDecimal sumNetAmountByCompanyId(@Param("companyId") UUID companyId);

    @Query("select coalesce(sum(c.netAmount), 0) from ShipmentCharge c "
            + "where c.companyId = :companyId "
            + "and c.shipmentId in (select s.id from Shipment s where s.companyId = :companyId "
            + "and s.bookingDate between :start and :end)")
    BigDecimal sumNetAmountByCompanyIdAndBookingDateBetween(@Param("companyId") UUID companyId,
                                                            @Param("start") LocalDate start,
                                                            @Param("end") LocalDate end);

    /** Backs "This Month's Collection" for a branch-scoped caller (BRANCH_MANAGER/
     *  BRANCH_OPERATOR) — the company-scoped sibling above leaked whole-company revenue
     *  to a branch-scoped caller. */
    @Query("select coalesce(sum(c.netAmount), 0) from ShipmentCharge c "
            + "where c.companyId = :companyId "
            + "and c.shipmentId in (select s.id from Shipment s where s.companyId = :companyId "
            + "and s.bookingBranchId = :bookingBranchId and s.bookingDate between :start and :end)")
    BigDecimal sumNetAmountByCompanyIdAndBookingBranchIdAndBookingDateBetween(
            @Param("companyId") UUID companyId, @Param("bookingBranchId") UUID bookingBranchId,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Backs the "Revenue Trend" chart for a company-bound caller — one row per day that
     *  had at least one charge; days with none simply don't appear (DashboardServiceImpl
     *  fills the gaps with zero). Theta-join (comma, not a mapped association) against
     *  {@code Shipment} for its {@code bookingDate} — same "no ORM association between
     *  these two entities" reasoning {@link #sumNetAmountForBookingDateBetween} already
     *  documents, just grouped instead of summed to one total. */
    @Query("select s.bookingDate as day, coalesce(sum(c.netAmount), 0) as revenue "
            + "from ShipmentCharge c, Shipment s "
            + "where c.shipmentId = s.id and c.companyId = :companyId and s.companyId = :companyId "
            + "and s.bookingDate between :start and :end group by s.bookingDate")
    List<DailyRevenueRow> dailyRevenueByCompanyIdAndBookingDateBetween(@Param("companyId") UUID companyId,
                                                                        @Param("start") LocalDate start,
                                                                        @Param("end") LocalDate end);

    /** Cross-tenant sibling of {@link #dailyRevenueByCompanyIdAndBookingDateBetween} — only
     *  safe from inside {@code CompanyContext.runAs(null, ...)}, same convention as every
     *  other unscoped method in this repository. */
    @Query("select s.bookingDate as day, coalesce(sum(c.netAmount), 0) as revenue "
            + "from ShipmentCharge c, Shipment s "
            + "where c.shipmentId = s.id and s.bookingDate between :start and :end group by s.bookingDate")
    List<DailyRevenueRow> dailyRevenueByBookingDateBetween(@Param("start") LocalDate start,
                                                            @Param("end") LocalDate end);

    /** Projection for both daily-revenue trend queries above. */
    interface DailyRevenueRow {
        LocalDate getDay();
        BigDecimal getRevenue();
    }
}
