package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Body of {@code PATCH /api/v1/branches/{id}/assign-manager}. A null id clears the
 * manager. The user must belong to the company; granting them the BRANCH_MANAGER role is
 * a separate User Management action.
 */
@Schema(name = "AssignManagerRequest", description = "The user to make branch manager; null clears it")
public record AssignManagerRequest(UUID managerId) {
}
