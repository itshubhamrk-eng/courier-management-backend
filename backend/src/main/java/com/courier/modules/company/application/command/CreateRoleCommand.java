package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.RoleType;

/**
 * Input to {@code RoleService.create}.
 *
 * <p>{@code companyId} is absent on purpose: the company comes from the caller's verified
 * JWT, never from the request body. Accepting it would let a company admin create a role
 * inside another company.
 *
 * <p>{@code isSystemRole} is absent for the same class of reason — only the platform
 * seeds system roles, and a caller that could set the flag could make its own role
 * undeletable.
 *
 * <p>Permissions are not accepted: Permission management is a separate module. A new
 * role starts with none and is useless until that module grants some, which is safer
 * than the alternative of guessing.
 */
public record CreateRoleCommand(
        String roleCode,
        String roleName,
        String description,
        RoleType roleType,
        Boolean defaultRole
) {
}
