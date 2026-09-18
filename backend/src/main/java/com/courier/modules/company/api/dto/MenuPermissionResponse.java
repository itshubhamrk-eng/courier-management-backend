package com.courier.modules.company.api.dto;

import com.courier.modules.company.application.UserPermissionService.MenuPermissionNode;
import com.courier.modules.company.domain.PermissionAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One node of a user's annotated menu tree. {@code roleDefault}/{@code effective}/
 * {@code overridden} are keyed by {@code CREATE}/{@code READ}/{@code UPDATE}/
 * {@code DELETE} — only the actions this node's module actually has; empty (and
 * {@code module} null) for a grouping node.
 */
@Schema(name = "MenuPermissionResponse")
public record MenuPermissionResponse(
        UUID id,
        String code,
        String title,
        String icon,
        String route,
        String module,
        int displayOrder,
        Map<PermissionAction, Boolean> roleDefault,
        Map<PermissionAction, Boolean> effective,
        Map<PermissionAction, Boolean> overridden,
        List<MenuPermissionResponse> children
) {

    public static MenuPermissionResponse from(MenuPermissionNode node) {
        return new MenuPermissionResponse(
                node.id(), node.code(), node.title(), node.icon(), node.route(),
                node.module() == null ? null : node.module().name(), node.displayOrder(),
                node.roleDefault(), node.effective(), node.overridden(),
                node.children().stream().map(MenuPermissionResponse::from).toList());
    }
}
