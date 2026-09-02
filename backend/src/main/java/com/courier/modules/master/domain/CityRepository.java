package com.courier.modules.master.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cities, within a district. */
public interface CityRepository extends MasterDataRepository<City> {

    long countByCompanyIdAndDistrictId(UUID companyId, UUID districtId);

    List<City> findByCompanyIdAndDistrictIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID districtId);

    /** Used by the postal-lookup auto-resolver, scoped to the parent district. */
    Optional<City> findByCompanyIdAndDistrictIdAndNameIgnoreCase(UUID companyId, UUID districtId, String name);
}
