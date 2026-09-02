package com.courier.modules.master.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Districts, within a state. */
public interface DistrictRepository extends MasterDataRepository<District> {

    long countByCompanyIdAndStateId(UUID companyId, UUID stateId);

    List<District> findByCompanyIdAndStateIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID stateId);

    /** Used by the postal-lookup auto-resolver, scoped to the parent state. */
    Optional<District> findByCompanyIdAndStateIdAndNameIgnoreCase(UUID companyId, UUID stateId, String name);
}
