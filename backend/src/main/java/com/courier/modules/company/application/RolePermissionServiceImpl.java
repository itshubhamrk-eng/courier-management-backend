package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySettingKeys;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Granting and revoking permissions on a company's roles.
 *
 * <p>Three rules do real work here, and each exists because of a specific failure:
 * <ol>
 *   <li><b>Plan gating.</b> A company cannot grant itself a right its subscription
 *       excludes. Checked against the {@code feature.*} settings seeded from the plan, so
 *       this module never reaches into the subscription module.</li>
 *   <li><b>Lockout protection.</b> The last role able to administer roles cannot have
 *       that ability removed — a company that revokes {@code ROLE_UPDATE} everywhere can
 *       no longer fix its own permissions, and only support can rescue it.</li>
 *   <li><b>Company scoping.</b> The role is loaded with an explicit company predicate,
 *       because a primary-key load bypasses the Hibernate filter.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.COMPANY_ADMIN + "')")
public class RolePermissionServiceImpl implements RolePermissionService {

    private static final String ENTITY = "RolePermission";

    /**
     * Without at least one role holding this, a company cannot change its own
     * permissions again.
     */
    private static final String ROLE_ADMIN_PERMISSION = "ROLE_UPDATE";

    private final CompanyRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CompanySettingRepository settingRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public GrantResult assign(UUID roleId, Collection<String> permissionCodes,
                              boolean replaceExisting) {
        UUID companyId = requireCompany();
        CompanyRole role = loadRole(roleId, companyId);

        Set<String> requested = normalise(permissionCodes);
        if (requested.isEmpty() && !replaceExisting) {
            throw new BusinessRuleException("No permissions were supplied.");
        }

        List<Permission> found = permissionRepository.findAllByPermissionCodeIn(requested);
        requireAllExist(requested, found);

        List<String> rejected = new ArrayList<>();
        List<String> granted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> revoked = new ArrayList<>();

        Map<String, Object> planFeatures = planFeatureFlags();

        // Captured before the writes: the guard must prevent *losing* the ability to
        // manage roles, not refuse every change in a company that never had it. Without
        // this, a company with no ROLE_UPDATE grant could not be given one.
        boolean couldManageRolesBefore = companyCanManageRoles(companyId);

        List<RolePermission> existing =
                rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(roleId);
        Set<String> held = existing.stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RolePermission> toSave = new ArrayList<>();
        for (Permission permission : found) {
            String code = permission.getPermissionCode();

            if (!permission.isActive()) {
                rejected.add(code);
                continue;
            }
            if (!permission.isAllowedByPlan(planFeatures)) {
                // Fails closed: an unknown or absent flag denies.
                rejected.add(code);
                continue;
            }
            if (held.contains(code)) {
                skipped.add(code);
                continue;
            }
            toSave.add(RolePermission.grant(roleId, permission));
            granted.add(code);
        }

        if (replaceExisting) {
            List<RolePermission> surplus = existing.stream()
                    .filter(rp -> !requested.contains(rp.getPermissionCode()))
                    .toList();
            surplus.forEach(rp -> {
                rp.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
                revoked.add(rp.getPermissionCode());
            });
            rolePermissionRepository.saveAll(surplus);
        }

        rolePermissionRepository.saveAll(toSave);

        // Run after the writes so the check sees the state the caller is asking for,
        // and rolls the whole transaction back if it would lock the company out.
        requireRoleAdminNotLost(companyId, couldManageRolesBefore);

        if (!granted.isEmpty() || !revoked.isEmpty()) {
            log.info("Role {} permissions changed by {}: +{} -{} (skipped {}, rejected {})",
                    role.getRoleCode(), currentActor(), granted.size(), revoked.size(),
                    skipped.size(), rejected.size());
            auditService.record(AuditAction.ROLE_PERMISSIONS_ASSIGNED, ENTITY, roleId,
                    Map.of("roleCode", role.getRoleCode(),
                            "granted", granted,
                            "revoked", revoked,
                            "rejected", rejected));
        }

        if (!rejected.isEmpty()) {
            log.warn("Role {}: {} permission(s) refused — inactive or outside the plan: {}",
                    role.getRoleCode(), rejected.size(), rejected);
        }

        return new GrantResult(granted, revoked, skipped, rejected);
    }

    @Override
    @Transactional
    public void revoke(UUID roleId, UUID permissionId) {
        UUID companyId = requireCompany();
        CompanyRole role = loadRole(roleId, companyId);

        Optional<RolePermission> grant =
                rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId);
        if (grant.isEmpty()) {
            // Idempotent: revoking a permission the role does not hold is not an error.
            return;
        }

        boolean couldManageRolesBefore = companyCanManageRoles(companyId);

        RolePermission rolePermission = grant.get();
        rolePermission.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        rolePermissionRepository.save(rolePermission);

        requireRoleAdminNotLost(companyId, couldManageRolesBefore);

        log.info("Permission {} revoked from role {} by {}",
                rolePermission.getPermissionCode(), role.getRoleCode(), currentActor());
        auditService.record(AuditAction.ROLE_PERMISSION_REVOKED, ENTITY, roleId,
                Map.of("roleCode", role.getRoleCode(),
                        "permissionCode", rolePermission.getPermissionCode()));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
    public List<RolePermission> listGrants(UUID roleId) {
        // A super admin has no bound company and may inspect any role for support; a
        // company admin is pinned to their own by the company predicate.
        CompanyContext.getCompanyId().ifPresent(companyId -> loadRole(roleId, companyId));
        return rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
    public List<Permission> listPermissions(UUID roleId) {
        List<String> codes = listGrants(roleId).stream()
                .map(RolePermission::getPermissionCode)
                .toList();
        return codes.isEmpty() ? List.of() : permissionRepository.findAllByPermissionCodeIn(codes);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
    public List<String> resolveEffectiveCodes(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return rolePermissionRepository.findAllByRoleIdIn(roleIds).stream()
                .map(RolePermission::getPermissionCode)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
    public java.util.Map<UUID, Integer> countByRoles(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        return rolePermissionRepository.findAllByRoleIdIn(roleIds).stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        Collectors.reducing(0, grant -> 1, Integer::sum)));
    }

    // -------------------------------------------------------------------- helpers

    private CompanyRole loadRole(UUID roleId, UUID companyId) {
        // Explicit company predicate: findById would bypass the Hibernate filter and let
        // one company grant permissions on another's role.
        return roleRepository.findByIdWithinCompany(roleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Permissions are granted to a "
                        + "company's own roles, so this must be done by a user of that company."));
    }

    private Set<String> normalise(Collection<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(Permission::normaliseCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** An unknown code is a client bug, not something to silently drop. */
    private void requireAllExist(Set<String> requested, List<Permission> found) {
        if (found.size() == requested.size()) {
            return;
        }
        Set<String> knownCodes = found.stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());
        List<String> unknown = requested.stream().filter(code -> !knownCodes.contains(code)).toList();
        if (!unknown.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Unknown permission code(s): " + String.join(", ", unknown));
        }
    }

    /**
     * The company's plan features, read from the settings seeded at company creation
     * rather than from the subscription module — those rows exist precisely so a
     * company-scoped caller can answer this without a cross-module lookup.
     */
    private Map<String, Object> planFeatureFlags() {
        return settingRepository
                .findAllByCategoryOrderBySettingKeyAsc(CompanySettingKeys.CATEGORY_FEATURES)
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getSettingKey()
                                .substring(CompanySettingKeys.FEATURE_PREFIX.length()),
                        setting -> Boolean.parseBoolean(setting.getSettingValue()),
                        (first, second) -> first));
    }

    /**
     * Refuses a change that would <em>take away</em> the company's ability to manage its
     * own roles.
     *
     * <p>The distinction matters: a company that already has no role holding
     * {@code ROLE_UPDATE} must still be able to grant it to one, so this only fires when
     * the ability existed before the change and does not after. Guarding on the
     * after-state alone would block every grant in exactly the situation that needs
     * fixing.
     */
    private void requireRoleAdminNotLost(UUID companyId, boolean couldManageRolesBefore) {
        if (!couldManageRolesBefore) {
            return;
        }
        if (!companyCanManageRoles(companyId)) {
            throw new BusinessRuleException(
                    "This would leave no active role able to manage roles (%s). "
                            .formatted(ROLE_ADMIN_PERMISSION)
                            + "Grant it to another role first.");
        }
    }

    /** Whether any <em>active</em> role in the company currently holds {@code ROLE_UPDATE}. */
    private boolean companyCanManageRoles(UUID companyId) {
        return permissionRepository.findByPermissionCode(ROLE_ADMIN_PERMISSION)
                .map(roleAdmin -> rolePermissionRepository
                        .findRoleIdsByPermissionId(roleAdmin.getId()).stream()
                        .map(id -> roleRepository.findByIdWithinCompany(id, companyId))
                        .flatMap(Optional::stream)
                        .anyMatch(CompanyRole::isActive))
                .orElse(false);
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
