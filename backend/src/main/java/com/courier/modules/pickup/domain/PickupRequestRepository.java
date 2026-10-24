package com.courier.modules.pickup.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickupRequestRepository extends JpaRepository<PickupRequest, UUID> {

    @Query("select p from PickupRequest p where p.id = :id and p.companyId = :companyId")
    Optional<PickupRequest> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
            select p from PickupRequest p
            where p.companyId = :companyId
              and (:status is null or p.status = :status)
              and (:pdpBranchId is null or p.pdpBranchId = :pdpBranchId)
              and (:search is null or lower(p.pickupNo) like lower(concat('%', :search, '%'))
                   or lower(p.contactName) like lower(concat('%', :search, '%'))
                   or p.contactMobile like concat('%', :search, '%')
                   or p.pincode like concat('%', :search, '%'))
            order by p.createdAt desc""")
    Page<PickupRequest> search(@Param("companyId") UUID companyId, @Param("status") PickupStatus status,
                               @Param("pdpBranchId") UUID pdpBranchId, @Param("search") String search,
                               Pageable pageable);

    @Query("select p from PickupRequest p where p.companyId = :companyId and p.shipmentId = :shipmentId")
    Optional<PickupRequest> findByShipment(@Param("companyId") UUID companyId, @Param("shipmentId") UUID shipmentId);

    @Query("select count(p) from PickupRequest p where p.companyId = :companyId and p.createdAt >= :from and p.createdAt < :to")
    long countCreatedBetween(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select p.status, count(p) from PickupRequest p where p.companyId = :companyId "
            + "and (:pdpBranchId is null or p.pdpBranchId = :pdpBranchId) group by p.status")
    List<Object[]> countByStatus(@Param("companyId") UUID companyId, @Param("pdpBranchId") UUID pdpBranchId);

    /** Collected pickups since {@code from}, per partner. Dashboard figure. */
    @Query("select p.pdpBranchId, count(p) from PickupRequest p where p.companyId = :companyId and p.pdpBranchId is not null "
            + "and p.pickedUpAt >= :from group by p.pdpBranchId")
    List<Object[]> countPickedUpSince(@Param("companyId") UUID companyId, @Param("from") Instant from);

    /** Pickups assigned to a partner that nobody has accepted yet, oldest first. */
    @Query("select p from PickupRequest p where p.companyId = :companyId and p.status in :statuses "
            + "and (:pdpBranchId is null or p.pdpBranchId = :pdpBranchId) order by p.createdAt asc")
    List<PickupRequest> findOpenByStatus(@Param("companyId") UUID companyId, @Param("statuses") java.util.Collection<PickupStatus> statuses,
                                         @Param("pdpBranchId") UUID pdpBranchId, org.springframework.data.domain.Pageable limit);
}
