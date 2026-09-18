package com.courier.modules.company.api.dto;

import com.courier.modules.company.application.MenuService.MenuNode;
import com.courier.modules.company.domain.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "MenuItemResponse", description = "One node of the menu/submenu hierarchy")
public record MenuItemResponse(
        UUID id,
        UUID parentId,
        String code,
        String title,
        String icon,
        String route,
        @Schema(description = "The permission module this leaf's CRUD checkboxes read/write, "
                + "null for a grouping node")
        String permissionModule,
        int displayOrder,
        boolean active,
        List<MenuItemResponse> children
) {

    public static MenuItemResponse from(MenuNode node) {
        MenuItem item = node.item();
        return new MenuItemResponse(
                item.getId(), item.getParentId(), item.getCode(), item.getTitle(), item.getIcon(),
                item.getRoute(), item.getPermissionModule() == null ? null : item.getPermissionModule().name(),
                item.getDisplayOrder(), item.isActive(),
                node.children().stream().map(MenuItemResponse::from).toList());
    }
}
