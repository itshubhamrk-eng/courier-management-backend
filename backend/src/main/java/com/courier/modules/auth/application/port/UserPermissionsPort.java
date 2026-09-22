package com.courier.modules.auth.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * The auth module's view of a user's effective permissions.
 *
 * <p>This seam is why token issuance could depend on the permission-code JWT claim
 * without auth reaching into the company module's role/override tables directly. The
 * only implementation is {@code UserPermissionsDirectory} in {@code modules/company},
 * backed by {@code UserPermissionService.resolveEffectivePermissionCodes} — the same
 * function the User Permissions screen itself calls, so there is exactly one place this
 * is computed.
 */
public interface UserPermissionsPort {

    /**
     * Role codes unioned with granted overrides, minus revoked overrides. Never null.
     *
     * @param roleNames the JWT-authority role names already resolved for this user
     *                  ({@code User.roleNames()}) — a platform account ({@code
     *                  SUPER_ADMIN}) holds no {@code user_company_roles} row (it is not a
     *                  company's own role), so the company-role lookup this normally runs
     *                  resolves to nothing for it; the implementation uses this set to
     *                  recognise that case and grant the platform's own rights instead.
     */
    Set<String> resolveEffectivePermissions(UUID userId, Set<String> roleNames);
}
