package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreatePermissionRequest;
import com.courier.modules.company.api.dto.PermissionResponse;
import com.courier.modules.company.api.dto.PermissionSearchRequest;
import com.courier.modules.company.api.dto.RolePermissionResponse;
import com.courier.modules.company.api.dto.UpdatePermissionRequest;
import com.courier.modules.company.application.PermissionService;
import com.courier.modules.company.application.RolePermissionService;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

/** Wire contract ↔ application/domain types for the permission catalogue and grants. */
@Component
public class PermissionMapper {

    public PermissionService.CreatePermissionCommand toCommand(CreatePermissionRequest request) {
        return new PermissionService.CreatePermissionCommand(
                request.module(),
                request.action(),
                request.permissionName(),
                request.resource(),
                request.description(),
                request.displayOrder(),
                request.requiredFeatureFlag());
    }

    public PermissionService.UpdatePermissionCommand toCommand(UpdatePermissionRequest request) {
        return new PermissionService.UpdatePermissionCommand(
                request.permissionName(),
                request.resource(),
                request.description(),
                request.displayOrder(),
                request.requiredFeatureFlag(),
                request.version());
    }

    public PermissionCriteria toCriteria(PermissionSearchRequest request) {
        PermissionSearchRequest safe = request == null ? PermissionSearchRequest.empty() : request;
        return new PermissionCriteria(
                safe.module(),
                safe.action(),
                safe.status(),
                safe.isSystemPermission(),
                safe.resource(),
                safe.planGatedOnly(),
                safe.search());
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModule(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription(),
                permission.isSystemPermission(),
                permission.getStatus(),
                permission.getDisplayOrder(),
                permission.getRequiredFeatureFlag(),
                permission.getCreatedBy(),
                // Columns are created_at/updated_at; the API says createdDate/updatedDate.
                permission.getCreatedAt(),
                permission.getUpdatedBy(),
                permission.getUpdatedAt(),
                permission.getVersion());
    }

    public RolePermissionResponse toResponse(CompanyRole role,
                                             RolePermissionService.GrantResult result,
                                             List<String> effective) {
        return new RolePermissionResponse(
                role.getId(),
                role.getRoleCode(),
                result.granted(),
                result.revoked(),
                result.skipped(),
                result.rejected(),
                effective);
    }
}
