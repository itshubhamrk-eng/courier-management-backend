package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Result of {@code GET /api/v1/global-masters/pincodes/lookup/{code}}.
 *
 * <p>{@code matched=false} means the postal directory has no record of this pincode (or
 * the lookup is disabled/unreachable) — the create form falls back to the manual Area
 * picker, it does not treat this as an error.
 */
@Schema(name = "PincodeAreaLookupResponse", description = "Area auto-resolved from a pincode via the postal directory")
public record PincodeAreaLookupResponse(
        boolean matched,
        UUID areaId, String areaName,
        String cityName, String districtName, String stateName, String countryName,
        String postOfficeName,
        @Schema(description = "How many post offices this pincode maps to upstream — 1 was used.")
        int alternateCount,
        @Schema(description = "Every Area this pincode will link once saved — the same preview " +
                "the detail page's own \"Areas served\" card shows after creation, primary first.")
        List<PincodeAreaPreview> areas
) {
    public static PincodeAreaLookupResponse notFound() {
        return new PincodeAreaLookupResponse(false, null, null, null, null, null, null, null, 0, List.of());
    }

    public record PincodeAreaPreview(UUID areaId, String areaName, String cityName, boolean primary) {
    }
}
