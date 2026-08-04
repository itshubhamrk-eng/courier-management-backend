package com.courier.modules.company.application;

import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionCriteria;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.PermissionSpecifications;
import com.courier.modules.company.domain.PermissionStatus;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalogue use cases.
 *
 * <p>Per-method {@code @PreAuthorize}: {@code SUPER_ADMIN} writes, and both admin tiers
 * read. The vocabulary is platform-wide, so a company admin may consult it but never
 * change it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String ENTITY = "Permission";
    private static final String SUPER_ADMIN_ONLY = "hasRole('" + Roles.SUPER_ADMIN + "')";
    private static final String ADMIN_READERS =
            "hasAnyRole('" + Roles.SUPER_ADMIN + "', '" + Roles.COMPANY_ADMIN + "')";

    private final PermissionRepository repository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public Permission create(CreatePermissionCommand command) {
        if (command.module() == null || command.action() == null) {
            throw new BusinessRuleException("A permission needs both a module and an action.");
        }

        String code = Permission.codeFor(command.module(), command.action());
        // Counts soft-deleted rows: the unique key does not know about `deleted`, so
        // without this the caller gets an opaque 409 from the constraint.
        if (repository.isCodeTaken(code)) {
            throw new DuplicateResourceException(ENTITY, "permissionCode", code);
        }

        Permission permission = Permission.builder()
                .module(command.module())
                .action(command.action())
                .permissionName(command.permissionName())
                .resource(command.resource())
                .description(command.description())
                .displayOrder(command.displayOrder())
                // Only the seeding migration creates system permissions. A settable flag
                // would let an operator mint an undeletable, uneditable row by accident.
                .systemPermission(false)
                .status(PermissionStatus.ACTIVE)
                .requiredFeatureFlag(blankToNull(command.requiredFeatureFlag()))
                .build();

        permission.applyInvariants();
        Permission saved = repository.save(permission);

        log.info("Permission {} created by {}", saved.getPermissionCode(), currentActor());
        auditService.record(AuditAction.PERMISSION_CREATED, ENTITY, saved.getId(),
                Map.of("permissionCode", saved.getPermissionCode(),
                        "module", saved.getModule().name(),
                        "action", saved.getAction().name()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public Permission update(UUID id, UpdatePermissionCommand command) {
        Permission permission = loadOrThrow(id);
        // System permissions are read-only: their codes appear in @PreAuthorize
        // expressions and in every company's grants.
        permission.requireEditable();
        requireCurrentVersion(permission, command.expectedVersion());

        permission.setPermissionName(command.permissionName());
        permission.setResource(command.resource());
        permission.setDescription(command.description());
        permission.setDisplayOrder(command.displayOrder());
        permission.setRequiredFeatureFlag(blankToNull(command.requiredFeatureFlag()));

        permission.applyInvariants();
        Permission saved = repository.save(permission);

        log.info("Permission {} updated by {}", saved.getPermissionCode(), currentActor());
        auditService.record(AuditAction.PERMISSION_UPDATED, ENTITY, saved.getId(),
                Map.of("permissionCode", saved.getPermissionCode()));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN_READERS)
    public Permission getById(UUID id) {
        return loadOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN_READERS)
    public Permission getByCode(String permissionCode) {
        String code = Permission.normaliseCode(permissionCode);
        return repository.findByPermissionCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, code));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN_READERS)
    public Page<Permission> search(PermissionCriteria criteria, Pageable pageable) {
        return repository.findAll(PermissionSpecifications.matching(criteria), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN_READERS)
    public List<Permission> listGrantable() {
        return repository.findAllByStatusOrderByDisplayOrderAsc(PermissionStatus.ACTIVE);
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public void delete(UUID id) {
        Permission permission = loadOrThrow(id);
        permission.requireEditable();

        // Deleting a permission that roles still hold would strip access from every
        // company holding it, with no warning and no way to see what was lost.
        // Deactivating retires it from the catalogue while existing grants keep working.
        long grants = rolePermissionRepository.countGrantsAcrossAllCompanies(
                TimeOrderedUuid.toBytes(permission.getId()));
        if (grants > 0) {
            throw new BusinessRuleException(
                    "%s is granted to %d role(s) and cannot be deleted. Deactivate it instead."
                            .formatted(permission.getPermissionCode(), grants));
        }

        permission.deactivate();
        permission.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(permission);

        log.info("Permission {} soft deleted by {}", permission.getPermissionCode(), currentActor());
        auditService.record(AuditAction.PERMISSION_DELETED, ENTITY, permission.getId(),
                Map.of("permissionCode", permission.getPermissionCode()));
    }

    // -------------------------------------------------------------------- helpers

    private Permission loadOrThrow(UUID id) {
        // Platform-level entity: no company filter for findById to bypass.
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private void requireCurrentVersion(Permission permission, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(permission.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Permission.class, permission.getId());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
