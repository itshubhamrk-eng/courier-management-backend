package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** One pincode mapped to a branch. */
@Schema(name = "BranchPincodeResponse", description = "A pincode mapped to this branch")
public record BranchPincodeResponse(
        UUID id,
        UUID pincodeId,
        String pincodeCode,
        String pincodeName
) {
}
