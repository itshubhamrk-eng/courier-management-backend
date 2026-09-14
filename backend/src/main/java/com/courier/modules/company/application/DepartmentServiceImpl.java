package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateDepartmentCommand;
import com.courier.modules.company.application.command.UpdateDepartmentCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.Department;
import com.courier.modules.company.domain.DepartmentRepository;
import com.courier.modules.company.domain.DepartmentRole;
import com.courier.modules.company.domain.DepartmentRoleRepository;
import com.courier.modules.company.domain.DepartmentStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Department use cases. Mirrors {@code RoleServiceImpl}'s isolation shape — see that
 * class's own note on why every single-row load goes through an explicit company
 * predicate rather than {@code findById}.
 *
 * <p>A department's role grants travel with it: {@link #create} and {@link #update} take
 * the complete set of role ids the department should hold, the same "full replacement"
 * shape {@code RoleController}'s own PUT already uses, rather than a separate add/remove
 * endpoint pair.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private static final String ENTITY = "Department";
    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String COMPANY_ADMIN_OR_SUPER_ADMIN =
            "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '" + Roles.SUPER_ADMIN + "')";
    // A branch manager places their own new hires into a department (UserServiceImpl's
    // BRANCH_WRITERS) and needs the assignable list to do it — same bridge RoleService
    // grants its own ASSIGNABLE_READERS.
    private static final String ASSIGNABLE_READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.SUPER_ADMIN + "', '" + Roles.BRANCH_MANAGER + "')";

    private final DepartmentRepository repository;
    private final DepartmentRoleRepository departmentRoleRepository;
    private final CompanyRoleRepository roleRepository;
    private final CompanyUserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public Department create(CreateDepartmentCommand command) {
        UUID companyId = requireCompany();

        String departmentCode = Department.normaliseCode(command.departmentCode());
        String departmentName = command.departmentName() == null ? null : command.departmentName().trim();

        requireCodeAvailable(companyId, departmentCode, null);
        requireNameAvailable(companyId, departmentName, null);

        Department department = Department.builder()
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .description(command.description())
                .status(DepartmentStatus.ACTIVE)
                .build();

        department.applyInvariants();
        Department saved = repository.save(department);

        List<CompanyRole> roles = resolveRoles(companyId, command.roleIds());
        grantRoles(saved.getId(), roles);

        log.info("Department {} created in company {} by {}",
                saved.getDepartmentCode(), companyId, currentActor());
        auditService.record(AuditAction.DEPARTMENT_CREATED, ENTITY, saved.getId(),
                Map.of("departmentCode", saved.getDepartmentCode(),
                        "departmentName", saved.getDepartmentName(),
                        "roleCodes", roleCodes(roles)));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public Department update(UUID id, UpdateDepartmentCommand command) {
        UUID companyId = requireCompany();
        Department department = loadOrThrow(id, companyId);
        requireCurrentVersion(department, command.expectedVersion());

        String departmentName = command.departmentName() == null ? null : command.departmentName().trim();
        requireNameAvailable(companyId, departmentName, id);

        department.setDepartmentName(departmentName);
        department.setDescription(command.description());
        department.applyInvariants();
        Department saved = repository.save(department);

        List<CompanyRole> roles = resolveRoles(companyId, command.roleIds());
        replaceRoles(saved.getId(), roles);

        log.info("Department {} updated in company {} by {}",
                saved.getDepartmentCode(), companyId, currentActor());
        auditService.record(AuditAction.DEPARTMENT_UPDATED, ENTITY, saved.getId(),
                Map.of("departmentCode", saved.getDepartmentCode(),
                        "departmentName", saved.getDepartmentName(),
                        "roleCodes", roleCodes(roles)));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_OR_SUPER_ADMIN)
    public Department getById(UUID id) {
        return CompanyContext.getCompanyId()
                .map(companyId -> loadOrThrow(id, companyId))
                .orElseGet(() -> requireSuperAdminLoad(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_OR_SUPER_ADMIN)
    public List<Department> listAll() {
        requireCompany();
        return repository.findAllByOrderByDepartmentNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ASSIGNABLE_READERS)
    public List<Department> listAssignable() {
        requireCompany();
        return repository.findAllByStatusOrderByDepartmentNameAsc(DepartmentStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ASSIGNABLE_READERS)
    public List<CompanyRole> rolesOf(UUID departmentId) {
        UUID companyId = requireCompany();
        loadOrThrow(departmentId, companyId);

        List<UUID> roleIds = departmentRoleRepository.findAllByDepartmentIdOrderByRoleCodeAsc(departmentId).stream()
                .map(DepartmentRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllByIdInWithinCompany(roleIds, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ASSIGNABLE_READERS)
    public Map<UUID, List<CompanyRole>> rolesOf(List<UUID> departmentIds) {
        UUID companyId = requireCompany();
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Map.of();
        }

        List<DepartmentRole> grants = departmentRoleRepository.findAllByDepartmentIdIn(departmentIds);
        if (grants.isEmpty()) {
            return Map.of();
        }

        Set<UUID> roleIds = grants.stream().map(DepartmentRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, CompanyRole> rolesById = roleRepository.findAllByIdInWithinCompany(roleIds, companyId).stream()
                .collect(Collectors.toMap(CompanyRole::getId, r -> r));

        Map<UUID, List<CompanyRole>> byDepartment = new LinkedHashMap<>();
        for (DepartmentRole grant : grants) {
            CompanyRole role = rolesById.get(grant.getRoleId());
            if (role == null) {
                continue;
            }
            byDepartment.computeIfAbsent(grant.getDepartmentId(), k -> new ArrayList<>()).add(role);
        }
        return byDepartment;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public Department activate(UUID id) {
        UUID companyId = requireCompany();
        Department department = loadOrThrow(id, companyId);
        if (department.isActive()) {
            return department;
        }

        department.activate();
        Department saved = repository.save(department);

        log.info("Department {} activated in company {} by {}",
                saved.getDepartmentCode(), companyId, currentActor());
        auditService.record(AuditAction.DEPARTMENT_ACTIVATED, ENTITY, saved.getId(),
                Map.of("departmentCode", saved.getDepartmentCode()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public Department deactivate(UUID id) {
        UUID companyId = requireCompany();
        Department department = loadOrThrow(id, companyId);
        if (!department.isActive()) {
            return department;
        }

        department.deactivate();
        Department saved = repository.save(department);

        // Existing holders keep their placement and roles; deactivation only withdraws
        // the department from the picker offered to new users.
        log.info("Department {} deactivated in company {} by {}",
                saved.getDepartmentCode(), companyId, currentActor());
        auditService.record(AuditAction.DEPARTMENT_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("departmentCode", saved.getDepartmentCode()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public void delete(UUID id) {
        UUID companyId = requireCompany();
        Department department = loadOrThrow(id, companyId);

        if (userRepository.existsByDepartmentId(id)) {
            throw new BusinessRuleException(
                    "Department %s still has users placed in it and cannot be deleted. "
                            .formatted(department.getDepartmentCode())
                            + "Move them to another department first.");
        }

        department.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(department);

        log.info("Department {} soft deleted in company {} by {}",
                department.getDepartmentCode(), companyId, currentActor());
        auditService.record(AuditAction.DEPARTMENT_DELETED, ENTITY, department.getId(),
                Map.of("departmentCode", department.getDepartmentCode()));
    }

    // -------------------------------------------------------------------- helpers

    private Department loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private Department requireSuperAdminLoad(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Departments belong to a company, so "
                        + "this operation must be performed by a user of that company."));
    }

    private void requireCodeAvailable(UUID companyId, String departmentCode, UUID excludeId) {
        if (repository.isDepartmentCodeTaken(companyId, departmentCode, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "departmentCode", departmentCode);
        }
    }

    private void requireNameAvailable(UUID companyId, String departmentName, UUID excludeId) {
        if (repository.isDepartmentNameTaken(companyId, departmentName, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "departmentName", departmentName);
        }
    }

    /** Every requested id must resolve to a role of this company — an unknown id is a
     *  client bug, not something to silently drop. */
    private List<CompanyRole> resolveRoles(UUID companyId, List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requested = new LinkedHashSet<>(roleIds);
        List<CompanyRole> found = roleRepository.findAllByIdInWithinCompany(requested, companyId);
        if (found.size() != requested.size()) {
            Set<UUID> foundIds = found.stream().map(CompanyRole::getId).collect(Collectors.toSet());
            List<UUID> missing = requested.stream().filter(rid -> !foundIds.contains(rid)).toList();
            throw new ResourceNotFoundException("Role", missing.getFirst());
        }
        return found;
    }

    private void grantRoles(UUID departmentId, List<CompanyRole> roles) {
        List<DepartmentRole> grants = roles.stream()
                .map(role -> DepartmentRole.grant(departmentId, role))
                .toList();
        departmentRoleRepository.saveAll(grants);
    }

    /** Full replacement: whatever is not in {@code roles} any more is revoked, and
     *  whatever is new is granted. Mirrors {@code RolePermissionServiceImpl.assign}'s
     *  {@code replaceExisting} branch. */
    private void replaceRoles(UUID departmentId, List<CompanyRole> roles) {
        List<DepartmentRole> existing =
                departmentRoleRepository.findAllByDepartmentIdOrderByRoleCodeAsc(departmentId);
        Set<UUID> existingRoleIds = existing.stream().map(DepartmentRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> wantedRoleIds = roles.stream().map(CompanyRole::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DepartmentRole> surplus = existing.stream()
                .filter(grant -> !wantedRoleIds.contains(grant.getRoleId()))
                .toList();
        surplus.forEach(grant -> grant.softDelete(SecurityUtils.getCurrentUserId().orElse(null)));
        departmentRoleRepository.saveAll(surplus);

        List<DepartmentRole> additions = roles.stream()
                .filter(role -> !existingRoleIds.contains(role.getId()))
                .map(role -> DepartmentRole.grant(departmentId, role))
                .toList();
        departmentRoleRepository.saveAll(additions);
    }

    private List<String> roleCodes(List<CompanyRole> roles) {
        return roles.stream().map(CompanyRole::getRoleCode).sorted().toList();
    }

    private void requireCurrentVersion(Department department, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(department.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Department.class, department.getId());
        }
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
