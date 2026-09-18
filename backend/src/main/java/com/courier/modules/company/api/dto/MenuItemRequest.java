package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.PermissionModule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(name = "MenuItemRequest")
public record MenuItemRequest(
        UUID parentId,
        @NotBlank String code,
        @NotBlank String title,
        String icon,
        String route,
        @Schema(description = "One of the platform's permission modules, or null for a grouping node")
        PermissionModule permissionModule,
        int displayOrder,
        Boolean active
) {

    public MenuItem toEntity() {
        return MenuItem.builder()
                .parentId(parentId)
                .code(code)
                .title(title)
                .icon(icon)
                .route(route)
                .permissionModule(permissionModule)
                .displayOrder(displayOrder)
                .active(active == null || active)
                .build();
    }
}
