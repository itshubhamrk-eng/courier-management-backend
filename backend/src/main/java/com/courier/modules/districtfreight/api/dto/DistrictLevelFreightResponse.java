package com.courier.modules.districtfreight.api.dto;

import com.courier.modules.districtfreight.domain.DistrictFreightStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full representation of a District Level Freight row. {@code branchCode}/{@code
 * branchName}/{@code districtCode}/{@code districtName} are resolved server-side
 * (from {@code BranchLookupPort}/{@code DistrictLookupPort}) so the list page needs no
 * separate join — null only for a foreign id that failed to resolve, which should not
 * happen for a row this service itself validated on write.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "DistrictLevelFreightResponse", description = "District Level Freight rate row, in full")
public record DistrictLevelFreightResponse(
        UUID id, UUID companyId,
        UUID branchId, String branchCode, String branchName,
        UUID districtId, String districtCode, String districtName,
        BigDecimal rate1To15, BigDecimal rate16To50, BigDecimal rate51To100,
        BigDecimal rate101To1000, BigDecimal rate1001To1500, BigDecimal rate1501To2000,
        boolean odaApplicable, BigDecimal odaCharge,
        DistrictFreightStatus status,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
