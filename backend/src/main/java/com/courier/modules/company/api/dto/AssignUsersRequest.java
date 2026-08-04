package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PATCH /api/v1/branches/{id}/assign-users}. Places the listed users at
 * the branch. Ids that are not users of the company are reported back, not applied.
 */
@Schema(name = "AssignUsersRequest", description = "Users to place at the branch")
public record AssignUsersRequest(
        @NotEmpty @Size(max = 500) List<UUID> userIds
) {
}
