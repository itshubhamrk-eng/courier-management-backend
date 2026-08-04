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

    long countByBookingDate(LocalDate bookingDate);

    long countByStatus(ShipmentStatus status);

    long countByStatusIn(Collection<ShipmentStatus> statuses);

    List<Shipment> findTop5ByOrderByCreatedAtDesc();
}
