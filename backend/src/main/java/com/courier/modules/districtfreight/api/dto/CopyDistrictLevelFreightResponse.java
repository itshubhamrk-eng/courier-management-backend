package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "CopyDistrictLevelFreightResponse", description = "Per-target counts of copied and skipped rows")
public record CopyDistrictLevelFreightResponse(List<Target> targets) {

    public record Target(UUID branchId, String branchCode, int copied, int skipped) {
    }
}
