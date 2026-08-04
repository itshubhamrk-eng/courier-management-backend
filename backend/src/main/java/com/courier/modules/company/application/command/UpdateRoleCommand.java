package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.RoleType;

/**
 * Input to {@code RoleService.update}. A full replacement of the editable fields.
 *
 * <p>Absent by design: {@code roleCode} is immutable (users and audit rows reference it),
 * {@code companyId} comes from the JWT, {@code isSystemRole} is set only by the platform,
 * and {@code status} moves through the activate/deactivate endpoints so each change is
 * separately audited.
 *
 * <p>A system role may be renamed and re-described — a company calling its admins
 * "Owners" is reasonable — but never deleted.
 *
 * @param expectedVersion the version the client last read; a stale value is rejected
 *                        with {@code 409 CONCURRENT_MODIFICATION}
 */
public record UpdateRoleCommand(
        String roleName,
        String description,
        RoleType roleType,
        Boolean defaultRole,
        Long expectedVersion
) {
}
