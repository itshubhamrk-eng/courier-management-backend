package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserPermissionOverride;
import com.courier.modules.company.domain.UserPermissionOverrideRepository;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.domain.TimeOrderedUuid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * See {@link UserPermissionService}. Company/branch scoping mirrors
 * {@code UserServiceImpl}'s own {@code assignRole}/{@code removeRole} exactly — a
 * {@code BRANCH_MANAGER} may only touch their own branch's users, the same "assign
 * menus" responsibility that method's own javadoc already describes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private static final String ENTITY = "User";

    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";
    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.SUPER_ADMIN + "')";

    private static final Set<PermissionAction> CRUD_ACTIONS =
            java.util.EnumSet.of(PermissionAction.CREATE, PermissionAction.READ,
                    PermissionAction.UPDATE, PermissionAction.DELETE);

    private final CompanyUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    /**
     * The raw repository, not {@link RolePermissionService}: that service's own
     * {@code resolveEffectiveCodes} is {@code @PreAuthorize}-gated to admins, and
     * {@link #resolveEffectivePermissionCodes} below must also work for a non-admin user
     * resolving their own rights during login, before any admin-shaped SecurityContext
     * exists to satisfy that check.
     */
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final MenuItemRepository menuItemRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<String> resolveEffectivePermissionCodes(UUID userId) {
        Set<String> codes = new LinkedHashSet<>(roleCodesFor(userId));
        for (UserPermissionOverride override : overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(userId)) {
            if (override.isGranted()) {
                codes.add(override.getPermissionCode());
            } else {
                codes.remove(override.getPermissionCode());
            }
        }
        return codes.stream().sorted().toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public MenuPermissionNode getMenuPermissions(UUID userId) {
        UUID companyId = requireCompany();
        User target = loadWithinCompany(userId, companyId);
        requireManageableByCaller(SecurityUtils.requireCurrentUser(), target);

        Set<String> roleCodes = roleCodesFor(userId);
        Map<String, Boolean> overrideByCode = overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(userId)
                .stream()
                .collect(Collectors.toMap(UserPermissionOverride::getPermissionCode,
                        UserPermissionOverride::isGranted));

        List<MenuItem> all = menuItemRepository.findAllByOrderByDisplayOrderAsc();
        Map<UUID, List<MenuItem>> byParent = all.stream()
                .filter(m -> m.isActive())
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? ROOT : m.getParentId()));

        List<MenuPermissionNode> roots = buildNodes(null, byParent, roleCodes, overrideByCode);
        return new MenuPermissionNode(null, "root", "Menu", null, null, null, 0,
                Map.of(), Map.of(), Map.of(), roots);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public UserPermissionUpdateResult updateUserPermissions(UUID userId, List<MenuPermissionUpdateItem> items) {
        UUID companyId = requireCompany();
        User target = loadWithinCompany(userId, companyId);
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        requireManageableByCaller(caller, target);

        if (items == null || items.isEmpty()) {
            throw new BusinessRuleException("No menu permissions were supplied.");
        }

        Set<String> roleCodes = roleCodesFor(userId);
        Map<String, UserPermissionOverride> existingByCode =
                overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(userId).stream()
                        .collect(Collectors.toMap(UserPermissionOverride::getPermissionCode, o -> o));

        List<UUID> menuItemIds = items.stream().map(MenuPermissionUpdateItem::menuItemId).toList();
        Map<UUID, MenuItem> menuById = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, m -> m));

        List<String> granted = new ArrayList<>();
        List<String> revoked = new ArrayList<>();
        UUID actorId = SecurityUtils.getCurrentUserId().orElse(null);

        // Several menu leaves can name the same module (Shipment Booking/List/Tracking
        // all govern SHIPMENT_*) — they are the same underlying right on different
        // screens, so the client sends the same desired value on each; collapsing to one
        // entry per permission BEFORE writing anything is what keeps that from reading a
        // stale pre-loop snapshot twice and attempting two inserts for the same
        // (company, user, permission) tuple in one request.
        Map<Permission, Boolean> wantedByPermission = new LinkedHashMap<>();
        for (MenuPermissionUpdateItem item : items) {
            MenuItem menuItem = menuById.get(item.menuItemId());
            if (menuItem == null || menuItem.isGroup()) {
                // A group node (or an unknown id) carries no permission of its own —
                // silently ignored rather than a hard failure, so the client can submit
                // the whole tree (including group rows) in one shot without filtering it.
                continue;
            }
            Map<PermissionAction, Boolean> wanted = Map.of(
                    PermissionAction.CREATE, item.create(), PermissionAction.READ, item.read(),
                    PermissionAction.UPDATE, item.update(), PermissionAction.DELETE, item.delete());
            for (Permission permission : availablePermissions(menuItem.getPermissionModule())) {
                wantedByPermission.put(permission, Boolean.TRUE.equals(wanted.get(permission.getAction())));
            }
        }

        for (Map.Entry<Permission, Boolean> entry : wantedByPermission.entrySet()) {
            Permission permission = entry.getKey();
            String code = permission.getPermissionCode();
            boolean want = entry.getValue();
            boolean roleHas = roleCodes.contains(code);
            UserPermissionOverride existing = existingByCode.get(code);
            // What the user's effective right actually was before this call — an
            // override (if any) wins over the role default, same rule
            // resolveEffectivePermissionCodes applies.
            boolean before = existing != null ? existing.isGranted() : roleHas;

            if (want == roleHas) {
                // Matches the role default again: no override should remain.
                if (existing != null) {
                    existing.softDelete(actorId);
                    overrideRepository.save(existing);
                }
            } else if (existing != null) {
                existing.setGranted(want);
                overrideRepository.save(existing);
            } else {
                // A prior "reverted to default" may have left a soft-deleted row for this
                // exact (user, permission) — the unique key does not know about `deleted`,
                // so that row must be resurrected rather than inserted alongside.
                UserPermissionOverride resurrected = overrideRepository
                        .findAnyByUserAndPermissionIncludingDeleted(TimeOrderedUuid.toBytes(companyId),
                                TimeOrderedUuid.toBytes(userId), TimeOrderedUuid.toBytes(permission.getId()))
                        .orElse(null);
                if (resurrected != null) {
                    resurrected.restore();
                    resurrected.setGranted(want);
                    overrideRepository.save(resurrected);
                } else {
                    overrideRepository.save(UserPermissionOverride.of(userId, permission, want));
                }
            }

            if (before != want) {
                (want ? granted : revoked).add(code);
            }
        }

        if (!granted.isEmpty() || !revoked.isEmpty()) {
            log.info("User {} menu permissions changed by {}: +{} -{}",
                    target.getEmail(), currentActor(), granted.size(), revoked.size());
            auditService.record(AuditAction.USER_PERMISSIONS_UPDATED, ENTITY, userId,
                    Map.of("email", target.getEmail(), "granted", granted, "revoked", revoked));
        }

        return new UserPermissionUpdateResult(granted, revoked, resolveEffectivePermissionCodes(userId));
    }

    // -------------------------------------------------------------------- helpers

    /** Sentinel key for top-level nodes in the parent-grouped map — {@code null} cannot be a map key. */
    private static final UUID ROOT = new UUID(0L, 0L);

    private List<MenuPermissionNode> buildNodes(UUID parentId, Map<UUID, List<MenuItem>> byParent,
                                                Set<String> roleCodes, Map<String, Boolean> overrideByCode) {
        List<MenuItem> children = byParent.getOrDefault(parentId == null ? ROOT : parentId, List.of());
        List<MenuPermissionNode> nodes = new ArrayList<>();
        for (MenuItem item : children.stream()
                .sorted(Comparator.comparingInt(MenuItem::getDisplayOrder)).toList()) {
            List<MenuPermissionNode> kids = buildNodes(item.getId(), byParent, roleCodes, overrideByCode);

            Map<PermissionAction, Boolean> roleDefault = new EnumMap<>(PermissionAction.class);
            Map<PermissionAction, Boolean> effective = new EnumMap<>(PermissionAction.class);
            Map<PermissionAction, Boolean> overridden = new EnumMap<>(PermissionAction.class);

            if (!item.isGroup()) {
                for (Permission permission : availablePermissions(item.getPermissionModule())) {
                    boolean roleHas = roleCodes.contains(permission.getPermissionCode());
                    Boolean override = overrideByCode.get(permission.getPermissionCode());
                    roleDefault.put(permission.getAction(), roleHas);
                    overridden.put(permission.getAction(), override != null);
                    effective.put(permission.getAction(), override != null ? override : roleHas);
                }
            }

            nodes.add(new MenuPermissionNode(item.getId(), item.getCode(), item.getTitle(), item.getIcon(),
                    item.getRoute(), item.getPermissionModule(), item.getDisplayOrder(),
                    roleDefault, effective, overridden, kids));
        }
        return nodes;
    }

    /** This module's catalogue rows restricted to the four CRUD actions a menu checkbox offers. */
    private List<Permission> availablePermissions(PermissionModule module) {
        if (module == null) {
            return List.of();
        }
        return permissionRepository.findAllByModuleOrderByDisplayOrderAsc(module).stream()
                .filter(p -> CRUD_ACTIONS.contains(p.getAction()))
                .toList();
    }

    private Set<String> roleCodesFor(UUID userId) {
        List<UUID> roleIds = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(userId).stream()
                .map(UserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return rolePermissionRepository.findAllByRoleIdIn(roleIds).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private User loadWithinCompany(UUID id, UUID companyId) {
        return userRepository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. User permissions are scoped to a "
                        + "company, so this must be done by a user of that company."));
    }

    /** Same rule {@code UserServiceImpl.requireManageableByCaller} enforces: a company
     *  admin (or super admin) may touch any user; a branch manager only their own branch's. */
    private void requireManageableByCaller(AuthenticatedUser caller, User target) {
        if (caller.isSuperAdmin() || caller.hasRole(Roles.COMPANY_ADMIN)) {
            return;
        }
        if (caller.hasRole(Roles.BRANCH_MANAGER)) {
            User self = userRepository.findByIdWithinCompany(caller.userId(), caller.companyId()).orElse(null);
            if (self != null && self.getBranchId() != null
                    && Objects.equals(self.getBranchId(), target.getBranchId())) {
                return;
            }
        }
        throw new ForbiddenException("You may only manage permissions for users of your own branch.");
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
