package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreateRoleRequest;
import com.courier.modules.company.api.dto.RoleResponse;
import com.courier.modules.company.api.dto.RoleSearchRequest;
import com.courier.modules.company.api.dto.RoleSummaryResponse;
import com.courier.modules.company.api.dto.UpdateRoleRequest;
import com.courier.modules.company.application.command.CreateRoleCommand;
import com.courier.modules.company.application.command.UpdateRoleCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.RoleCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates between the wire contract and the application/domain types.
 *
 * <p>Hand-written, like the other mappers in this project — there is no MapStruct
 * dependency, and with flat records on both sides a generated mapper would only hide the
 * one thing worth seeing: that {@code companyId}, {@code isSystemRole} and {@code status}
 * are never mapped *in* from a request.
 */
@Component
public class RoleMapper {

    public CreateRoleCommand toCommand(CreateRoleRequest request) {
        return new CreateRoleCommand(
                request.roleCode(),
                request.roleName(),
                request.description(),
                request.roleType(),
                request.isDefault());
    }

    public UpdateRoleCommand toCommand(UpdateRoleRequest request) {
        return new UpdateRoleCommand(
                request.roleName(),
                request.description(),
                request.roleType(),
                request.isDefault(),
                request.version());
    }

    public RoleCriteria toCriteria(RoleSearchRequest request) {
        RoleSearchRequest safe = request == null ? RoleSearchRequest.empty() : request;
        return new RoleCriteria(
                safe.companyId(),
                safe.status(),
                safe.roleType(),
                safe.isSystemRole(),
                safe.isDefault(),
                safe.permissionCode(),
                safe.search());
    }

    /**
     * @param permissionCodes what the role holds, fetched by the caller from
     *                        {@code RolePermissionService} — grants live in their own
     *                        table now, so the entity cannot supply them
     */
    public RoleResponse toResponse(CompanyRole role, List<String> permissionCodes) {
        return new RoleResponse(
                role.getId(),
                role.getCompanyId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getRoleType(),
                role.isSystemRole(),
                role.isDefaultRole(),
                role.getStatus(),
                permissionCodes == null ? List.of() : permissionCodes.stream().sorted().toList(),
                role.getCreatedBy(),
                // Columns are created_at/updated_at; the API calls them
                // createdDate/updatedDate. Mapped here so neither name leaks.
                role.getCreatedAt(),
                role.getUpdatedBy(),
                role.getUpdatedAt(),
                role.getVersion());
    }

    /** Detail view without grants, for paths that do not need them. */
    public RoleResponse toResponse(CompanyRole role) {
        return toResponse(role, List.of());
    }

    /** @param permissionCount pre-counted by the caller in one batch, to avoid N+1 */
    public RoleSummaryResponse toSummary(CompanyRole role, int permissionCount) {
        return new RoleSummaryResponse(
                role.getId(),
                role.getCompanyId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleType(),
                role.isSystemRole(),
                role.isDefaultRole(),
                role.getStatus(),
                permissionCount,
                role.getVersion());
    }

    public RoleSummaryResponse toSummary(CompanyRole role) {
        return toSummary(role, 0);
    }
}
