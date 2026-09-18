package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.MenuPermissionResponse;
import com.courier.modules.company.api.dto.UserMenuPermissionUpdateRequest;
import com.courier.modules.company.api.dto.UserPermissionUpdateResponse;
import com.courier.modules.company.application.UserPermissionService;
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

import java.util.UUID;

/**
 * One user's menu permissions: their role's defaults, viewable and overridable one user
 * at a time without touching the role. {@code COMPANY_ADMIN} (any user of their company)
 * or {@code BRANCH_MANAGER} (their own branch's users only — the same "assign menus"
 * responsibility {@code UserController}'s role-assignment endpoints already grant them).
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/menu-permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Permissions", description = "A user's menu permissions, on top of their role's defaults")
public class UserPermissionController {

    private final UserPermissionService service;

    @GetMapping
    @Operation(summary = "A user's annotated menu tree",
            description = "Every menu node, each grantable leaf carrying its role default, "
                    + "effective value and whether the user has overridden it, per CRUD action.")
    public ApiResponse<MenuPermissionResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success(MenuPermissionResponse.from(service.getMenuPermissions(userId)));
    }

    @PutMapping
    @Operation(summary = "Save a user's menu permissions",
            description = """
                    Submits the whole tree's desired CRUD state in one call. Only the leaves
                    that actually differ from the role's default become override rows —
                    leaving a checkbox at the role default never changes the role itself, and
                    a later change to the role's own grants still flows through for every
                    leaf this user never overrode.
                    """)
    public ApiResponse<UserPermissionUpdateResponse> update(
            @PathVariable UUID userId, @Valid @RequestBody UserMenuPermissionUpdateRequest request) {
        var result = service.updateUserPermissions(userId, request.toCommands());
        return ApiResponse.success(UserPermissionUpdateResponse.from(result), "User permissions updated");
    }
}
