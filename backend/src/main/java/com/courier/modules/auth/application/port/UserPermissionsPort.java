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

    /** Role codes unioned with granted overrides, minus revoked overrides. Never null. */
    Set<String> resolveEffectivePermissions(UUID userId);
}
