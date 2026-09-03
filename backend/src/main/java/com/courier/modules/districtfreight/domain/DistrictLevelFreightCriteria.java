package com.courier.modules.districtfreight.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a District Level Freight search. Every field optional; null means
 * "do not constrain". There is no free-text {@code search} — neither branch name nor
 * district name is stored on this row (both are resolved from the lookup ports), so
 * "search by station/district" is the {@code branchIds}/{@code districtIds} filters
 * themselves, driven by the same picker dropdowns the create form uses.
 *
 * @param branchIds   match any of these From Stations
 * @param districtIds match any of these destination districts
 * @param statuses    match any of these statuses
 */
public record DistrictLevelFreightCriteria(
        Set<UUID> branchIds,
        Set<UUID> districtIds,
        Set<DistrictFreightStatus> statuses
) {

    public static DistrictLevelFreightCriteria none() {
        return new DistrictLevelFreightCriteria(null, null, null);
    }
}
