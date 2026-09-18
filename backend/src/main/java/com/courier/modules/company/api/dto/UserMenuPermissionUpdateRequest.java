package com.courier.modules.company.api.dto;

import com.courier.modules.company.application.UserPermissionService.MenuPermissionUpdateItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "UserMenuPermissionUpdateRequest")
public record UserMenuPermissionUpdateRequest(
        @NotEmpty List<@Valid Item> items
) {

    @Schema(name = "UserMenuPermissionItem")
    public record Item(
            @NotNull UUID menuItemId,
            boolean create,
            boolean read,
            boolean update,
            boolean delete
    ) {
        public MenuPermissionUpdateItem toCommand() {
            return new MenuPermissionUpdateItem(menuItemId, create, read, update, delete);
        }
    }

    public List<MenuPermissionUpdateItem> toCommands() {
        return items.stream().map(Item::toCommand).toList();
    }
}
