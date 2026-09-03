package com.courier.modules.districtfreight.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What District Level Freight needs to know about the (global) District master — the
 * "Destination District" end of a rate row.
 *
 * <p>Owned by this module; implemented in {@code modules/master}, which crosses into
 * {@code GlobalMasters.PLATFORM_COMPANY_ID} internally the same way
 * {@code BranchPincodeMappingService} already crosses into it to resolve a Pincode. The
 * caller here never sees that reserved id.
 */
public interface DistrictLookupPort {

    record DistrictRef(UUID districtId, String code, String name, boolean active) {
    }

    Optional<DistrictRef> findDistrict(UUID districtId);

    Map<UUID, DistrictRef> findDistricts(Collection<UUID> districtIds);

    /** Resolves a "District" cell from an Excel import by name alone, case-insensitively —
     *  the sheet does not name a state to disambiguate by. */
    Optional<DistrictRef> findDistrictByName(String name);
}
