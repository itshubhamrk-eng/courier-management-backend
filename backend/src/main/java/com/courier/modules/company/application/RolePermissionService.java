package com.courier.modules.company.application;

import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.RolePermission;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Grants: which of a company's roles hold which permissions.
 *
 * <p><b>{@code COMPANY_ADMIN} only, and only within their own company.</b> A
 * {@code SUPER_ADMIN} may read the catalogue but must not reach into a company and
 * change what its staff can do — that is the company's decision, and doing it for them
 * would be indistinguishable from a compromise.
 *
 * <p>Assignment is bulk by default: a permissions screen submits a whole set, and
 * applying that as one transaction avoids a role sitting half-configured between calls.
 */
public interface RolePermissionService {

    /**
     * Adds permissions to a role, ignoring ones it already holds.
     *
     * @param replaceExisting when true the role ends up holding exactly this set, and
     *                        anything else it had is revoked — what a "save" button on a
     *                        permission matrix means
     */
    GrantResult assign(UUID roleId, Collection<String> permissionCodes, boolean replaceExisting);

    /** Removes one permission from a role. Idempotent. */
    void revoke(UUID roleId, UUID permissionId);

    List<RolePermission> listGrants(UUID roleId);

    /** The permissions a role holds, resolved to catalogue rows for display. */
    List<Permission> listPermissions(UUID roleId);

    /**
     * Effective permission codes for a set of roles — what User Management will call to
     * decide what someone may do. Duplicates across roles collapse.
     */
    List<String> resolveEffectiveCodes(Collection<UUID> roleIds);

    /**
     * How many permissions each of these roles holds, in one query. A list screen needs
     * the number per row, and asking per row would be N+1 — across every company for a
     * super admin.
     */
    java.util.Map<UUID, Integer> countByRoles(Collection<UUID> roleIds);

    /**
     * @param granted  codes newly added
     * @param revoked  codes removed (only when replacing)
     * @param skipped  codes already held, so not re-added
     * @param rejected codes the company's subscription does not include
     */
    record GrantResult(List<String> granted,
                       List<String> revoked,
                       List<String> skipped,
                       List<String> rejected) {
    }
}
