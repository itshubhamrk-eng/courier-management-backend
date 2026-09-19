package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySettingKeys;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gives a newly-provisioned account the company's own {@code BRANCH_MANAGER} or
 * {@code COMPANY_ADMIN} role — whichever it was created to hold.
 *
 * <p>"Creating a branch creates a default branch manager role" is satisfied by the role the
 * company already has, not by a new role per branch. A role per branch would put one row in
 * {@code company_roles} for every office a courier opens — a hundred branches would mean a
 * hundred rows saying the same thing, and re-permissioning "branch managers" would then mean
 * editing a hundred of them. What actually has to be true is that the account created with
 * the branch can manage it on day one, and that the role backing it exists. The same is true
 * of a company's founding admin: one {@code COMPANY_ADMIN} row per company, not one per admin.
 *
 * <p>So this is an <b>ensure</b>, not a create. The role is normally seeded at company
 * creation by {@link CompanyProvisioningService}; it is created here only when it is
 * missing, which happens to a company provisioned before the catalogue carried it, or one
 * whose administrator deleted it. Both leave an account with a JWT authority and no
 * permissioned role behind it — which is exactly the state {@code UserProvisioningService}
 * used to leave every newly-provisioned admin in: it set the JWT authority and stopped.
 *
 * <p><b>Two different "roles" are being set, and they are not the same thing.</b> Auth's
 * {@code Role.BRANCH_MANAGER}/{@code Role.COMPANY_ADMIN} is the JWT authority every
 * {@code @PreAuthorize} reads today, and {@code UserProvisioningService} sets it. This class
 * writes the {@code user_company_roles} grant — the permissioned role the company manages,
 * which also backs {@code UserPermissionService.resolveEffectivePermissionCodes} (the JWT
 * {@code permissions} claim). Setting only the first is what left a branch account with no
 * role in the roles screen, and a provisioned admin with an empty {@code permissions} claim
 * and a nav bare of anything permission-gated, despite a correct {@code roles} claim.
 *
 * <p>Runs inside the caller's transaction and the caller's bound company: branch creation is
 * already a {@code COMPANY_ADMIN} request with {@code CompanyContext} bound from the JWT, and
 * admin provisioning runs inside {@code CompanyContext.runAs}, so the company-owned reads
 * here filter to the right company either way without an explicit {@code runAs} of their own.
 * An account that committed without its role would be exactly the half-provisioned state the
 * single transaction exists to prevent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchRoleProvisioningService {

    private static final String ENTITY = "CompanyRole";

    private final CompanyRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CompanySettingRepository settingRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    /**
     * @param role        the company's role
     * @param roleCreated whether it had to be created because the company had none
     * @param granted     whether the grant was written, or the user already held it
     */
    public record BranchManagerRoleAssignment(CompanyRole role, boolean roleCreated, boolean granted) {
    }

    /**
     * Ensures the company's default branch-manager role exists and that {@code userId}
     * holds it.
     *
     * <p>Idempotent on both halves, because branch creation is not the only path here: a
     * branch code reused after a soft delete, or a retried create, must not produce a second
     * role or a duplicate grant. The unique key
     * {@code uk_user_company_roles_user_role} is the backstop for a race.
     *
     * @param companyId the owning company — the caller's, never taken from a request body
     * @param userId    the branch's account
     */
    @Transactional
    public BranchManagerRoleAssignment ensureBranchManagerRole(UUID companyId, UUID userId) {
        return ensureRole(companyId, userId, DefaultRoleCatalog.BRANCH_MANAGER, "branch created");
    }

    /**
     * Ensures the company's {@code COMPANY_ADMIN} role exists and that {@code userId} holds
     * it — the same gap {@link #ensureBranchManagerRole} closes for a branch account, for a
     * newly-provisioned company admin instead. See {@code UserProvisioningServiceImpl
     * #provisionAdmin}, the only caller: it sets the JWT authority and used to stop there.
     *
     * @param companyId the owning company — the caller's, never taken from a request body
     * @param userId    the admin's account
     */
    @Transactional
    public BranchManagerRoleAssignment ensureCompanyAdminRole(UUID companyId, UUID userId) {
        return ensureRole(companyId, userId, DefaultRoleCatalog.COMPANY_ADMIN, "admin provisioned");
    }

    private BranchManagerRoleAssignment ensureRole(UUID companyId, UUID userId, String roleCode, String reason) {
        boolean roleCreated = false;
        CompanyRole role = roleRepository.findByRoleCode(roleCode).orElse(null);

        if (role == null) {
            role = createFromCatalogue(companyId, roleCode, reason);
            roleCreated = true;
        } else if (!role.isActive()) {
            // A deactivated role is withdrawn from the assignment list, and an account
            // holding one would be a role that grants nothing. Reactivating it is the
            // smaller surprise: the company asked for this account, and the account needs
            // the role it was created to hold.
            role.activate();
            role = roleRepository.save(role);
            log.info("Reactivated the {} role in company {} — {}", roleCode, companyId, reason);
        }

        boolean granted = false;
        if (!userRoleRepository.existsByUserIdAndRoleId(userId, role.getId())) {
            userRoleRepository.save(UserRole.assign(userId, role));
            granted = true;
            auditService.record(AuditAction.USER_ROLE_ASSIGNED, "User", userId,
                    Map.of("roleCode", role.getRoleCode(),
                            "roleId", role.getId().toString(),
                            "reason", reason));
        }

        return new BranchManagerRoleAssignment(role, roleCreated, granted);
    }

    /**
     * Recreates the catalogue's {@code BRANCH_MANAGER} entry, permissions and all.
     *
     * <p>Plan gating reads the company's seeded {@code feature.*} settings rather than the
     * subscription module — decision 30, and the same source
     * {@code RolePermissionServiceImpl} uses, so a role created here can never hold a right
     * one granted through the roles screen would refuse.
     */
    private CompanyRole createFromCatalogue(UUID companyId, String roleCode, String reason) {
        DefaultRoleCatalog.RoleDefinition definition = DefaultRoleCatalog.definitions().stream()
                .filter(candidate -> candidate.code().equals(roleCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "DefaultRoleCatalog no longer defines " + roleCode
                                + "; " + reason + " depends on it"));

        CompanyRole role = roleRepository.save(CompanyRole.builder()
                .roleCode(definition.code())
                .roleName(definition.name())
                .description(definition.description())
                .roleType(definition.type())
                .systemRole(true)
                .defaultRole(false)
                .status(RoleStatus.ACTIVE)
                .build());

        Set<String> codes = DefaultRoleCatalog.permissionsFor(definition, planFeatureFlags());
        List<RolePermission> grants = new ArrayList<>();
        for (Permission permission : permissionRepository.findAllByPermissionCodeIn(codes)) {
            grants.add(RolePermission.grant(role.getId(), permission));
        }
        rolePermissionRepository.saveAll(grants);

        log.info("Company {} had no {} role; created it with {} permission(s) — {}",
                companyId, definition.code(), grants.size(), reason);
        auditService.record(AuditAction.ROLE_CREATED, ENTITY, role.getId(),
                Map.of("roleCode", role.getRoleCode(),
                        "permissionCount", grants.size(),
                        "reason", reason));

        return role;
    }

    /** @see RolePermissionServiceImpl#planFeatureFlags() — same rows, same fail-closed rule. */
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
}
