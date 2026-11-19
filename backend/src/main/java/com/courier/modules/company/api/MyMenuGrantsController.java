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
import java.util.UUID;

/** What the signed-in user's own sidebar reads: menus an admin explicitly unticked for them. */
@RestController
@RequestMapping("/api/v1/menu-grants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Permissions", description = "A user's menu permissions, on top of their role's defaults")
public class MyMenuGrantsController {

    private final UserPermissionService service;

    public record MyMenusResponse(List<String> deniedMenus, List<String> allowedMenus) { }

    @GetMapping("/me")
    @Operation(summary = "The caller's menus",
            description = "deniedMenus: node ids unticked for the caller personally (hide them). "
                    + "allowedMenus: node ids their roles hold, minus those denied; "
                    + "the sidebar of a custom-role user is exactly this list.")
    public ApiResponse<MyMenusResponse> mine() {
        UUID userId = SecurityUtils.requireCurrentUser().userId();
        return ApiResponse.success(new MyMenusResponse(
                service.deniedMenuCodes(userId), service.allowedMenuCodes(userId)));
    }
}
