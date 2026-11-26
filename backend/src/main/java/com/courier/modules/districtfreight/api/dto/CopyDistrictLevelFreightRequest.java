package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Body of {@code POST /api/v1/district-level-freight/copy}. {@code COMPANY_ADMIN} only. */
@Schema(name = "CopyDistrictLevelFreightRequest",
        description = "Copy one branch's active District Level Freight rows to one or more other branches")
public record CopyDistrictLevelFreightRequest(

        @NotNull @Schema(description = "Branch whose rates are copied") UUID sourceBranchId,
        @NotEmpty @Schema(description = "Branches that receive the rates; the source is not allowed here")
        List<@NotNull UUID> targetBranchIds
) {
}
