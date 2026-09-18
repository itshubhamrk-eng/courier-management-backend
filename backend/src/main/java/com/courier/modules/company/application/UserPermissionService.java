package com.courier.modules.company.application;

import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A user's effective permissions: their role(s)' defaults, overridable one user at a
 * time without touching the role.
 *
 * <p>There is deliberately no "assign role defaults to a new user" operation — a new
 * user starts with zero rows in {@code user_permission_overrides}, so
 * {@link #resolveEffectivePermissionCodes} already returns exactly their role's grants.
 * The moment an admin saves a change through {@link #updateUserPermissions}, only the
 * codes that actually differ from the role default become override rows; everything
 * else keeps tracking the role, including future changes to it.
 */
public interface UserPermissionService {

    /**
     * Role codes unioned with granted overrides, minus revoked overrides. The single
     * function both JWT issuance ({@code UserPermissionsPort}) and the permission-matrix
     * screen call — there is exactly one way this is computed.
     *
     * <p>Deliberately <b>not</b> {@code @PreAuthorize}-gated: this runs during login/
     * token-refresh, before a normal authenticated {@code SecurityContext} exists for the
     * caller to be checked against, and {@code userId} is always either the token's own
     * subject or a target already authorised by the calling method.
     */
    List<String> resolveEffectivePermissionCodes(UUID userId);

    /**
     * The full menu tree, each grantable leaf annotated with the role default, the
     * effective value and whether the user has overridden it, for each of that leaf
     * module's available CRUD actions.
     */
    MenuPermissionNode getMenuPermissions(UUID userId);

    /**
     * Diffs the requested matrix against the user's role defaults and persists only the
     * deltas — a leaf left at its role default gets no override row (or has a stale one
     * removed).
     */
    UserPermissionUpdateResult updateUserPermissions(UUID userId, List<MenuPermissionUpdateItem> items);

    record MenuPermissionUpdateItem(UUID menuItemId, boolean create, boolean read,
                                    boolean update, boolean delete) {
    }

    /** One node of the annotated tree. {@code module}/action maps are null for a group node. */
    record MenuPermissionNode(UUID id, String code, String title, String icon, String route,
                              PermissionModule module, int displayOrder,
                              Map<PermissionAction, Boolean> roleDefault,
                              Map<PermissionAction, Boolean> effective,
                              Map<PermissionAction, Boolean> overridden,
                              List<MenuPermissionNode> children) {
    }

    record UserPermissionUpdateResult(List<String> granted, List<String> revoked,
                                      List<String> effectivePermissions) {
    }
}
