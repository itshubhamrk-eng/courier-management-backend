package com.courier.modules.districtfreight.application;

import java.util.List;
import java.util.UUID;

/** Outcome of copying one branch's District Level Freight rows to other branches. */
public record CopyDistrictFreightResult(List<TargetResult> targets) {

    public record TargetResult(UUID branchId, String branchCode, int copied, int skipped) {
    }
}
