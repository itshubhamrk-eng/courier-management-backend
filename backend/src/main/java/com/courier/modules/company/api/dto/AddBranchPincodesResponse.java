package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Outcome of mapping pincodes to a branch: what was added, skipped, and refused. */
@Schema(name = "AddBranchPincodesResponse", description = "Result of mapping pincodes to a branch")
public record AddBranchPincodesResponse(
        List<BranchPincodeResponse> added,
        List<UUID> alreadyMapped,
        List<PincodeConflict> conflicts
) {
    /** A pincode already owned by a different branch of the same company. */
    @Schema(name = "PincodeConflict", description = "A pincode already mapped to another branch")
    public record PincodeConflict(UUID pincodeId, String pincodeCode, UUID branchId, String branchCode) {
    }
}
