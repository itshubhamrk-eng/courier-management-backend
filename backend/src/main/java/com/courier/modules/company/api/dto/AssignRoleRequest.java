package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of {@code POST /api/v1/users/{id}/roles}. */
@Schema(name = "AssignRoleRequest", description = "A company role to assign to the user")
public record AssignRoleRequest(@NotNull UUID roleId) {
}
