package com.courier.modules.company.api;

import com.courier.modules.company.application.UserPermissionService;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What the signed-in user's own sidebar reads: menus an admin explicitly unticked for them. */
@RestController
@RequestMapping("/api/v1/menu-grants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Permissions", description = "A user's menu permissions, on top of their role's defaults")
public class MyMenuGrantsController {

    private final UserPermissionService service;

    public record DeniedMenusResponse(List<String> deniedMenus) { }

    @GetMapping("/me")
    @Operation(summary = "Menus unticked for the caller",
            description = "Node ids to hide from the caller's sidebar. Empty when nothing was unticked.")
    public ApiResponse<DeniedMenusResponse> mine() {
        return ApiResponse.success(new DeniedMenusResponse(
                service.deniedMenuCodes(SecurityUtils.requireCurrentUser().userId())));
    }
}
