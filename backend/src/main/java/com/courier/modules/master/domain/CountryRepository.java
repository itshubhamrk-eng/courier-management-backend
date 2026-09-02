package com.courier.modules.master.domain;

import java.util.Optional;
import java.util.UUID;

/** Countries. The root of the hierarchy, so nothing here refers upward. */
public interface CountryRepository extends MasterDataRepository<Country> {

    /** Case-insensitive: used by the postal-lookup auto-resolver to find an existing
     *  row before creating a duplicate for a name spelled differently in casing only. */
    Optional<Country> findByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
