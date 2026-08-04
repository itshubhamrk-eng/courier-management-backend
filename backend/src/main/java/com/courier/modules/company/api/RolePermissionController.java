package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.PermissionResponse;
import com.courier.modules.company.api.dto.RolePermissionRequest;
import com.courier.modules.company.api.dto.RolePermissionResponse;
import com.courier.modules.company.application.RolePermissionService;
import com.courier.modules.company.application.RoleService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Which permissions a company's role holds.
 *
 * <p>{@code COMPANY_ADMIN} only for changes, and only within their own company — a
 * {@code SUPER_ADMIN} may read a role's grants for support but must not alter what
 * someone else's staff can do.
 *
 * <p>Users inherit permissions through roles; nothing here touches users.
 */
@RestController
@RequestMapping("/api/v1/roles/{roleId}/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Role Permissions", description = "Granting permissions to a company's roles")
public class RolePermissionController {

    private final RolePermissionService service;
    private final RoleService roleService;
    private final PermissionMapper mapper;

    @PostMapping
    @Operation(summary = "Assign permissions to a role",
            description = """
                    Bulk by design — a permission matrix submits the whole set in one
                    transaction, so a role is never left half-configured.

                    `replaceExisting=true` makes the role hold **exactly** the supplied
                    set, revoking anything else; false only adds. Permissions the role
                    already has are skipped, not duplicated.

                    Two things are refused rather than silently applied, and both come
                    back in the response:
                    * a permission outside the company's **subscription plan**
                    * a permission that has been **deactivated** in the catalogue

                    A change that would leave the company with no active role holding
                    `ROLE_UPDATE` is rejected outright — otherwise the company can no
                    longer manage its own permissions.
                    """)
    public ApiResponse<RolePermissionResponse> assign(
            @PathVariable UUID roleId,
            @Valid @RequestBody RolePermissionRequest request) {

        RolePermissionService.GrantResult result =
                service.assign(roleId, request.permissionCodes(), request.replace());

        List<String> effective = service.resolveEffectiveCodes(List.of(roleId));

        return ApiResponse.success(
                mapper.toResponse(roleService.getById(roleId), result, effective),
                "Role permissions updated");
    }

    @GetMapping
    @Operation(summary = "List a role's permissions",
            description = "The catalogue rows the role currently holds, in code order. "
                    + "A `COMPANY_ADMIN` sees only their own company's roles; a "
                    + "`SUPER_ADMIN` may inspect any.")
    public ApiResponse<List<PermissionResponse>> list(@PathVariable UUID roleId) {
        return ApiResponse.success(service.listPermissions(roleId).stream()
                .map(mapper::toResponse)
                .toList());
    }

    @DeleteMapping("/{permissionId}")
    @Operation(summary = "Revoke one permission from a role",
            description = """
                    Idempotent: revoking something the role does not hold succeeds.

                    Rejected if it would leave the company with no active role able to
                    manage roles. Users holding this role lose the right at their next
                    authorisation check.
                    """)
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID roleId,
                                                    @PathVariable UUID permissionId) {
        service.revoke(roleId, permissionId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Permission revoked"));
    }
}
