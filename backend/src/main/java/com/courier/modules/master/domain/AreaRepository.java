package com.courier.modules.master.domain;

import java.util.List;
import java.util.UUID;

/** Areas, within a city. */
public interface AreaRepository extends MasterDataRepository<Area> {

    long countByCompanyIdAndCityId(UUID companyId, UUID cityId);

    List<Area> findByCompanyIdAndCityIdOrderByDisplayOrderAscNameAsc(UUID companyId, UUID cityId);
}
