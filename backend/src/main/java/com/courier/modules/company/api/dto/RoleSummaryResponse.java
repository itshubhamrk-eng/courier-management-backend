package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Compact projection for list responses and role pickers.
 *
 * <p>Drops the permission set: a page of roles would otherwise carry hundreds of strings
 * nobody renders, and for a {@code SUPER_ADMIN} listing across every company that adds up
 * fast. Clients fetch {@code GET /roles/{id}} for the detail.
 *
 * <p>{@code permissionCount} is kept because "how much does this role grant" is exactly
 * the question a list view needs to answer.
 */
@Schema(name = "RoleSummaryResponse", description = "Company role, list projection")
public record RoleSummaryResponse(

        UUID id,
        UUID companyId,
        String roleCode,
        String roleName,
        RoleType roleType,
        boolean isSystemRole,
        boolean isDefault,
        RoleStatus status,
        int permissionCount,
        Long version
) {
}
