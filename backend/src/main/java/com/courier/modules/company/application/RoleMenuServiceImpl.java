package com.courier.modules.company.application;

import com.courier.modules.company.application.command.RoleMenuItem;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RoleMenu;
import com.courier.modules.company.domain.RoleMenuRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores a role's menus in {@code role_menu}. Saving also syncs the role's module permissions:
 * every ticked action grants {@code <MODULE>_<ACTION>}, and a module's permission is removed
 * only when no remaining ticked menu of the role needs it. Modules the role never had a menu
 * for are never touched, so permissions with no menu (e.g. {@code ROLE_UPDATE}) survive.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.COMPANY_ADMIN + "')")
public class RoleMenuServiceImpl implements RoleMenuService {

    private final CompanyRoleRepository roleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final MenuItemRepository menuItemRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RolePermissionService rolePermissionService;

    @Override
    @Transactional(readOnly = true)
    public List<RoleMenuItem> get(UUID roleId) {
        loadRole(roleId);
        return roleMenuRepository.findAllByRoleId(roleId).stream().map(RoleMenuServiceImpl::toItem).toList();
    }

    @Override
    @Transactional
    public List<RoleMenuItem> replace(UUID roleId, List<RoleMenuItem> items) {
        UUID companyId = requireCompany();
        loadRole(roleId);

        Map<UUID, RoleMenuItem> desired = new LinkedHashMap<>();
        for (RoleMenuItem item : items) {
            if (item.anyTicked()) {
                desired.put(item.menuItemId(), item);
            }
        }
        Map<UUID, RoleMenu> existing = roleMenuRepository.findAllByRoleId(roleId).stream()
                .collect(Collectors.toMap(RoleMenu::getMenuItemId, r -> r, (a, b) -> a, LinkedHashMap::new));

        Set<UUID> lookupIds = new LinkedHashSet<>(existing.keySet());
        lookupIds.addAll(desired.keySet());
        Map<UUID, MenuItem> menus = menuItemRepository.findAllById(lookupIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, m -> m));
        for (UUID id : desired.keySet()) {
            if (!menus.containsKey(id)) {
                throw new ResourceNotFoundException("MenuItem", id);
            }
        }
        desired.keySet().removeIf(id -> menus.get(id).isGroup());

        List<RoleMenu> changes = new ArrayList<>();
        for (RoleMenuItem item : desired.values()) {
            RoleMenu row = existing.get(item.menuItemId());
            if (row == null) {
                row = RoleMenu.builder().roleId(roleId).menuItemId(item.menuItemId()).build();
                row.setCompanyId(companyId);
            }
            row.setCanCreate(item.canCreate());
            row.setCanRead(item.canRead());
            row.setCanUpdate(item.canUpdate());
            row.setCanDelete(item.canDelete());
            changes.add(row);
        }
        // Hard delete: this is pure assignment data, and a soft-deleted row would keep holding the
        // (role, menu) unique key, so re-ticking that menu later would fail as a duplicate.
        List<RoleMenu> removed = existing.entrySet().stream()
                .filter(e -> !desired.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        roleMenuRepository.deleteAll(removed);
        roleMenuRepository.saveAll(changes);

        syncModulePermissions(roleId, existing.keySet(), desired, menus);
        return desired.values().stream().toList();
    }

    private void syncModulePermissions(UUID roleId, Set<UUID> previousMenuIds,
                                       Map<UUID, RoleMenuItem> desired, Map<UUID, MenuItem> menus) {
        Set<String> touchedModules = new LinkedHashSet<>();
        previousMenuIds.stream().map(menus::get)
                .filter(m -> m != null && !m.isGroup())
                .forEach(m -> touchedModules.add(m.getPermissionModule().name()));

        Set<String> wanted = new TreeSet<>();
        desired.values().forEach(item -> {
            String module = menus.get(item.menuItemId()).getPermissionModule().name();
            touchedModules.add(module);
            if (item.canCreate()) wanted.add(module + "_CREATE");
            if (item.canRead()) wanted.add(module + "_READ");
            if (item.canUpdate()) wanted.add(module + "_UPDATE");
            if (item.canDelete()) wanted.add(module + "_DELETE");
        });
        // Only codes the catalogue really has: a module may lack e.g. a DELETE action.
        Set<String> needed = permissionRepository.findAllByPermissionCodeIn(wanted).stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> finalCodes = rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(roleId).stream()
                .map(RolePermission::getPermissionCode)
                .filter(code -> !touchedModules.contains(moduleOf(code)))
                .collect(Collectors.toCollection(TreeSet::new));
        finalCodes.addAll(needed);

        rolePermissionService.assign(roleId, finalCodes, true);
    }

    /** {@code NOTIFICATION_READ} -> {@code NOTIFICATION}; {@code PDP_RATE_READ} -> {@code PDP_RATE}. */
    private static String moduleOf(String permissionCode) {
        int cut = permissionCode.lastIndexOf('_');
        return cut < 0 ? permissionCode : permissionCode.substring(0, cut);
    }

    private static RoleMenuItem toItem(RoleMenu row) {
        return new RoleMenuItem(row.getMenuItemId(), row.isCanCreate(), row.isCanRead(),
                row.isCanUpdate(), row.isCanDelete());
    }

    private CompanyRole loadRole(UUID roleId) {
        // Explicit company predicate: findById would bypass the Hibernate filter.
        return roleRepository.findByIdWithinCompany(roleId, requireCompany())
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Role menus belong to a company's own roles."));
    }
}
