package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.RoleMenuRequest;
import com.courier.modules.company.api.dto.RoleMenuResponse;
import com.courier.modules.company.application.RoleMenuService;
import com.courier.modules.company.application.command.RoleMenuItem;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles/{roleId}/menus")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Role Menus", description = "The menus a company role holds; users with the role follow them")
public class RoleMenuController {

    private final RoleMenuService service;

    @GetMapping
    @Operation(summary = "List a role's menus")
    public ApiResponse<List<RoleMenuResponse>> list(@PathVariable UUID roleId) {
        return ApiResponse.success(service.get(roleId).stream().map(RoleMenuController::toResponse).toList());
    }

    @PutMapping
    @Operation(summary = "Replace a role's menus",
            description = "Sends the whole set; a menu not listed (or with every action unticked) is removed. "
                    + "The role's module permissions are kept in step so the APIs behind a ticked menu work.")
    public ApiResponse<List<RoleMenuResponse>> replace(@PathVariable UUID roleId,
                                                       @Valid @RequestBody RoleMenuRequest request) {
        List<RoleMenuItem> items = request.menus().stream()
                .map(e -> new RoleMenuItem(e.menuItemId(), e.canCreate(), e.canRead(), e.canUpdate(), e.canDelete()))
                .toList();
        return ApiResponse.success(
                service.replace(roleId, items).stream().map(RoleMenuController::toResponse).toList(),
                "Role menus updated");
    }

    private static RoleMenuResponse toResponse(RoleMenuItem i) {
        return new RoleMenuResponse(i.menuItemId(), i.canCreate(), i.canRead(), i.canUpdate(), i.canDelete());
    }
}
