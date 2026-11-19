package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "RoleMenuRequest", description = "The full set of menus a role holds")
public record RoleMenuRequest(
        @NotNull
        @Size(max = 300, message = "at most 300 menus may be assigned in one request")
        List<@Valid RoleMenuEntry> menus
) {

    @Schema(name = "RoleMenuEntry", description = "One menu and the actions ticked for it")
    public record RoleMenuEntry(
            @NotNull UUID menuItemId,
            boolean canCreate,
            boolean canRead,
            boolean canUpdate,
            boolean canDelete
    ) {
    }
}
