package com.courier.modules.master.domain;

import java.util.List;
import java.util.UUID;

/** Districts, within a state. */
public interface DistrictRepository extends MasterDataRepository<District> {

    long countByCompanyIdAndStateId(UUID companyId, UUID stateId);

    List<District> findByCompanyIdAndStateIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID stateId);
}
