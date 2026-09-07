package com.courier.modules.master.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Areas, within a city. */
public interface AreaRepository extends MasterDataRepository<Area> {

    long countByCompanyIdAndCityId(UUID companyId, UUID cityId);

    List<Area> findByCompanyIdAndCityIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID cityId);

    /** Used to resolve a District/State filter on Pincode down to the area ids it covers. */
    List<Area> findByCompanyIdAndCityIdIn(UUID companyId, Collection<UUID> cityIds);

    /** Used by the postal-lookup auto-resolver, scoped to the parent city. */
    Optional<Area> findByCompanyIdAndCityIdAndNameIgnoreCase(UUID companyId, UUID cityId, String name);
}
