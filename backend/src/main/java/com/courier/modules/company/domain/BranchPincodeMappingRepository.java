package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Which branch owns delivery/service for a pincode. See {@link BranchPincodeMapping}. */
public interface BranchPincodeMappingRepository extends JpaRepository<BranchPincodeMapping, UUID> {

    List<BranchPincodeMapping> findByCompanyIdAndBranchIdOrderByCreatedAtAsc(UUID companyId, UUID branchId);

    /** At most one row per (company, pincode) — the whole point of the unique key. */
    Optional<BranchPincodeMapping> findByCompanyIdAndPincodeId(UUID companyId, UUID pincodeId);

    Optional<BranchPincodeMapping> findByIdAndCompanyIdAndBranchId(UUID id, UUID companyId, UUID branchId);
}
