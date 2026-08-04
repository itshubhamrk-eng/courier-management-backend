package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateRoleCommand;
import com.courier.modules.company.application.command.UpdateRoleCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.RoleCriteria;
import com.courier.modules.company.domain.RoleSpecifications;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Role use cases.
 *
 * <p><b>Authorisation is per method, not per class</b>, because reads and writes have
 * different audiences: a {@code SUPER_ADMIN} may look at any company's roles while
 * investigating, but only a {@code COMPANY_ADMIN} may change what their own staff can do.
 * Both role strings are folded from the {@link Roles} constants at compile time, so a
 * rename cannot leave a stale literal inside a SpEL expression the compiler never checks.
 *
 * <p><b>Company isolation has two layers</b>, and the second exists because the first is
 * easy to defeat:
 * <ol>
 *   <li>The Hibernate filter narrows every query to the bound company.</li>
 *   <li>Every single-row load goes through {@code findByIdWithinCompany} with the company
 *       from {@code CompanyContext}. A primary-key load bypasses the filter entirely, so
 *       without this a company admin could fetch — and edit — another company's role by
 *       guessing an id.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final String ENTITY = "Role";
    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String COMPANY_ADMIN_OR_SUPER_ADMIN =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')";
    // A branch manager staffs their own branch (UserServiceImpl's BRANCH_WRITERS) and
    // needs a role to assign — the assignable-roles picker, and only that one, admits them.
    private static final String ASSIGNABLE_READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.SUPER_ADMIN + "', '" + Roles.BRANCH_MANAGER + "')";

    private final CompanyRoleRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRole create(CreateRoleCommand command) {
        UUID companyId = requireCompany();

        String roleCode = CompanyRole.normaliseCode(command.roleCode());
        String roleName = command.roleName() == null ? null : command.roleName().trim();

        requireCodeAvailable(companyId, roleCode, null);
        requireNameAvailable(companyId, roleName, null);

        boolean isDefault = Boolean.TRUE.equals(command.defaultRole());

        CompanyRole role = CompanyRole.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .description(command.description())
                .roleType(command.roleType() == null ? RoleType.OPERATIONS : command.roleType())
                // Only the platform seeds system roles. A caller-settable flag would let
                // a company mint itself an undeletable role.
                .systemRole(false)
                .defaultRole(isDefault)
                .status(RoleStatus.ACTIVE)
                .build();

        role.applyInvariants();
        CompanyRole saved = repository.save(role);

        if (isDefault) {
            demoteOtherDefaults(companyId, saved.getId());
        }

        log.info("Role {} created in company {} by {}", saved.getRoleCode(), companyId, currentActor());
        auditService.record(AuditAction.ROLE_CREATED, ENTITY, saved.getId(),
                Map.of("roleCode", saved.getRoleCode(),
                        "roleName", saved.getRoleName(),
                        "roleType", saved.getRoleType().name(),
                        "isDefault", saved.isDefaultRole()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRole update(UUID id, UpdateRoleCommand command) {
        UUID companyId = requireCompany();
        CompanyRole role = loadOrThrow(id, companyId);
        requireCurrentVersion(role, command.expectedVersion());

        String roleName = command.roleName() == null ? null : command.roleName().trim();
        requireNameAvailable(companyId, roleName, id);

        Map<String, Object> before = snapshot(role);
        boolean isDefault = Boolean.TRUE.equals(command.defaultRole());

        // A system role may be renamed and re-described — calling your admins "Owners"
        // is reasonable — but its code and system flag stay put.
        role.setRoleName(roleName);
        role.setDescription(command.description());
        role.setRoleType(command.roleType() == null ? role.getRoleType() : command.roleType());
        role.setDefaultRole(isDefault);

        role.applyInvariants();
        CompanyRole saved = repository.save(role);

        if (isDefault) {
            demoteOtherDefaults(companyId, saved.getId());
        }

        Map<String, Object> changes = changeDetails(before, snapshot(saved));

        log.info("Role {} updated in company {} by {} ({} field(s) changed)",
                saved.getRoleCode(), companyId, currentActor(), changes.size());
        auditService.record(AuditAction.ROLE_UPDATED, ENTITY, saved.getId(), changes);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_OR_SUPER_ADMIN)
    public CompanyRole getById(UUID id) {
        // A super admin has no company of their own and may read any company's role; a
        // company admin is pinned to theirs.
        return CompanyContext.getCompanyId()
                .map(companyId -> loadOrThrow(id, companyId))
                .orElseGet(() -> requireSuperAdminLoad(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_OR_SUPER_ADMIN)
    public Page<CompanyRole> search(RoleCriteria criteria, Pageable pageable) {
        RoleCriteria safe = criteria == null ? RoleCriteria.none() : criteria;

        // For a company admin the Hibernate filter already restricts the query, but the
        // criteria are pinned to their company as well: a companyId supplied in the query
        // string must never widen the result, only narrow it.
        RoleCriteria effective = CompanyContext.getCompanyId()
                .map(safe::withCompanyId)
                .orElse(safe);

        return repository.findAll(RoleSpecifications.matching(effective), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ASSIGNABLE_READERS)
    public List<CompanyRole> listAssignable() {
        requireCompany();
        return repository.findAllByStatusOrderByRoleCodeAsc(RoleStatus.ACTIVE);
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRole activate(UUID id) {
        UUID companyId = requireCompany();
        CompanyRole role = loadOrThrow(id, companyId);
        if (role.isActive()) {
            // Idempotent, and no audit noise for a no-op.
            return role;
        }

        role.activate();
        CompanyRole saved = repository.save(role);

        log.info("Role {} activated in company {} by {}",
                saved.getRoleCode(), companyId, currentActor());
        auditService.record(AuditAction.ROLE_ACTIVATED, ENTITY, saved.getId(),
                Map.of("roleCode", saved.getRoleCode()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRole deactivate(UUID id) {
        UUID companyId = requireCompany();
        CompanyRole role = loadOrThrow(id, companyId);
        if (!role.isActive()) {
            return role;
        }

        // The default role must stay assignable: new users would otherwise be created
        // with a role nobody is allowed to hold.
        if (role.isDefaultRole()) {
            throw new BusinessRuleException(
                    "Role %s is the company's default and cannot be deactivated. "
                            .formatted(role.getRoleCode())
                            + "Make another role the default first.");
        }

        role.deactivate();
        CompanyRole saved = repository.save(role);

        // Existing holders keep the role; deactivation only withdraws it from the list
        // offered when assigning.
        log.info("Role {} deactivated in company {} by {}",
                saved.getRoleCode(), companyId, currentActor());
        auditService.record(AuditAction.ROLE_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("roleCode", saved.getRoleCode()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        CompanyRole role = loadOrThrow(id, companyId);

        if (role.isSystemRole()) {
            throw new BusinessRuleException(
                    "%s is a system role and cannot be deleted. Deactivate it instead."
                            .formatted(role.getRoleCode()));
        }

        if (role.isDefaultRole()) {
            throw new BusinessRuleException(
                    "Role %s is the company's default and cannot be deleted. "
                            .formatted(role.getRoleCode())
                            + "Make another role the default first.");
        }

        // Soft delete only, per the project invariant. Users currently holding the role
        // are not touched here — reassigning them belongs to User Management, which is
        // a separate module.
        role.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(role);

        log.info("Role {} soft deleted in company {} by {}",
                role.getRoleCode(), companyId, currentActor());
        auditService.record(AuditAction.ROLE_DELETED, ENTITY, role.getId(),
                Map.of("roleCode", role.getRoleCode()));
    }

    // -------------------------------------------------------------------- helpers

    /**
     * Loads within the caller's company. Never {@code findById}: a primary-key load
     * bypasses the Hibernate filter, and 404 rather than 403 is deliberate — telling a
     * caller "this exists but is not yours" leaks the existence of other companies' data.
     */
    private CompanyRole loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * Platform-level read. Safe only because no company is bound, which the
     * {@code @PreAuthorize} on the caller has already established means a super admin.
     */
    private CompanyRole requireSuperAdminLoad(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * Writes are meaningless without a company. A super admin reaching a write path
     * would arrive here with no company bound; {@code @PreAuthorize} already refuses
     * them, and this is the second line of defence.
     */
    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Roles belong to a company, so this "
                        + "operation must be performed by a user of that company."));
    }

    /**
     * Uniqueness is checked against soft-deleted rows too, because the unique key
     * {@code (company_id, role_code)} does not know about {@code deleted}. Without this
     * the caller would get an opaque 409 from the constraint instead of a message naming
     * the field.
     */
    private void requireCodeAvailable(UUID companyId, String roleCode, UUID excludeId) {
        if (repository.isRoleCodeTaken(companyId, roleCode, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "roleCode", roleCode);
        }
    }

    private void requireNameAvailable(UUID companyId, String roleName, UUID excludeId) {
        if (repository.isRoleNameTaken(companyId, roleName, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "roleName", roleName);
        }
    }

    /**
     * At most one default role per company. MySQL has no partial unique index, so this
     * is enforced here: promoting a role demotes whichever one held the flag.
     */
    private void demoteOtherDefaults(UUID companyId, UUID keepId) {
        List<CompanyRole> others = repository.findOtherDefaultRoles(companyId, keepId);
        if (others.isEmpty()) {
            return;
        }
        others.forEach(CompanyRole::clearDefault);
        repository.saveAll(others);
        log.info("Demoted {} previous default role(s) in company {}", others.size(), companyId);
    }

    /**
     * Explicit optimistic-lock check. {@code @Version} alone only catches a conflict
     * between load and flush inside one transaction; the real hazard is two admins
     * editing the same role across two requests, where the second silently overwrites
     * the first.
     */
    private void requireCurrentVersion(CompanyRole role, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(role.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(CompanyRole.class, role.getId());
        }
    }

    private Map<String, Object> snapshot(CompanyRole role) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("roleName", role.getRoleName());
        values.put("description", role.getDescription());
        values.put("roleType", role.getRoleType() == null ? null : role.getRoleType().name());
        values.put("isDefault", role.isDefaultRole());
        values.put("status", role.getStatus() == null ? null : role.getStatus().name());
        return values;
    }

    /** Only what changed reaches the audit trail. */
    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> pair = new HashMap<>();
                pair.put("from", oldValue);
                pair.put("to", newValue);
                changes.put(field, pair);
            }
        });
        return changes;
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
