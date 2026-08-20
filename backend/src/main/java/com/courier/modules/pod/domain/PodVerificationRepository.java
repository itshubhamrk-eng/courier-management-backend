package com.courier.modules.pod.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PodVerificationRepository extends JpaRepository<PodVerification, UUID> {

    @Query("select v from PodVerification v where v.shipmentId = :shipmentId "
            + "and v.companyId = :companyId order by v.createdAt desc")
    java.util.List<PodVerification> findAllByShipmentIdWithinCompany(
            @Param("shipmentId") UUID shipmentId, @Param("companyId") UUID companyId);

    default Optional<PodVerification> findLatestByShipmentIdWithinCompany(UUID shipmentId, UUID companyId) {
        return findAllByShipmentIdWithinCompany(shipmentId, companyId).stream().findFirst();
    }

    /** Every verification currently awaiting a human decision, within the caller's company —
     *  the Manual Review screen's worklist. */
    @Query("select v from PodVerification v where v.companyId = :companyId "
            + "and v.verificationStatus = com.courier.modules.pod.domain.PodVerificationStatus.REVIEW "
            + "order by v.createdAt asc")
    java.util.List<PodVerification> findAllPendingReviewWithinCompany(@Param("companyId") UUID companyId);

    /** Any prior run — any shipment, any status — whose photo hash matches this one, within
     *  the same company. Used for duplicate-POD detection; excludes the run being replaced
     *  (a re-verify of the exact same shipment+photo is not itself suspicious). */
    @Query("select v from PodVerification v where v.companyId = :companyId and v.podHash = :podHash "
            + "and v.shipmentId <> :shipmentId order by v.createdAt asc")
    java.util.List<PodVerification> findDuplicatesWithinCompany(
            @Param("companyId") UUID companyId, @Param("podHash") String podHash,
            @Param("shipmentId") UUID shipmentId);
}
