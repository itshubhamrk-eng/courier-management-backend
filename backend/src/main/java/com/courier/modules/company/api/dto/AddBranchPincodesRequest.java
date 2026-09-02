package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/branches/{id}/pincodes}. Pincodes already mapped to this
 * branch are reported back in {@code alreadyMapped}, not re-applied; pincodes already
 * mapped to a <em>different</em> branch are reported in {@code conflicts}, not moved —
 * removing them from that branch first is a deliberate, separate action.
 */
@Schema(name = "AddBranchPincodesRequest", description = "Pincodes to map to the branch")
public record AddBranchPincodesRequest(
        @NotEmpty @Size(max = 500) List<UUID> pincodeIds
) {
}
