package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "RoleMenuResponse", description = "A menu a role holds and the actions ticked for it")
public record RoleMenuResponse(UUID menuItemId, boolean canCreate, boolean canRead,
                               boolean canUpdate, boolean canDelete) {
}
