package com.courier.modules.districtfreight.api.dto;

import com.courier.modules.districtfreight.domain.DistrictFreightStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

/** Query parameters of {@code GET /api/v1/district-level-freight}, bound as a parameter
 *  object. No free-text {@code search}: "search by station/district" is these two
 *  id filters, driven by the same picker dropdowns the form uses. */
@Schema(name = "DistrictLevelFreightSearchRequest", description = "District Level Freight search filters")
public record DistrictLevelFreightSearchRequest(
        Set<UUID> branchId,
        Set<UUID> districtId,
        Set<DistrictFreightStatus> status
) {
    public static DistrictLevelFreightSearchRequest empty() {
        return new DistrictLevelFreightSearchRequest(null, null, null);
    }
}
