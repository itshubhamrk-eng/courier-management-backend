package com.courier.modules.manifest.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ManifestRepository extends JpaRepository<Manifest, UUID>,
        JpaSpecificationExecutor<Manifest> {

    @Query("select m from Manifest m where m.id = :id and m.companyId = :companyId")
    Optional<Manifest> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndManifestNumber(UUID companyId, String manifestNumber);

    /** Backs the Company Overview "Manifests Awaiting Dispatch" action-required tile. */
    long countByCompanyIdAndStatus(UUID companyId, ManifestStatus status);

    /** Backs the Branch Overview "Manifests Awaiting Dispatch" tile: manifests originating
     *  from the caller's own branch specifically, not the whole company. */
    long countByCompanyIdAndBookingBranchIdAndStatus(UUID companyId, UUID bookingBranchId, ManifestStatus status);
}
