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
}
