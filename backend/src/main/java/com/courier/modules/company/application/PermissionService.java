package com.courier.modules.company.application;

import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionCriteria;
import com.courier.modules.company.domain.PermissionModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * The permission catalogue — what rights exist on the platform.
 *
 * <p><b>Writes are {@code SUPER_ADMIN} only.</b> A permission is a word the whole
 * platform's authorisation vocabulary is written in: {@code @PreAuthorize} expressions
 * and every company's roles reference these codes, so one company must not be able to
 * add, rename or retire one.
 *
 * <p>Reads are open to {@code COMPANY_ADMIN} as well — a company admin building a role
 * has to see what can be granted.
 *
 * <p>Granting permissions to roles is {@link RolePermissionService}.
 */
public interface PermissionService {

    /** Creates a custom (non-system) permission. {@code SUPER_ADMIN} only. */
    Permission create(CreatePermissionCommand command);

    /** Edits a custom permission. System permissions are read-only and rejected. */
    Permission update(UUID id, UpdatePermissionCommand command);

    Permission getById(UUID id);

    Permission getByCode(String permissionCode);

    Page<Permission> search(PermissionCriteria criteria, Pageable pageable);

    /** The catalogue grouped by module, for a permission matrix screen. */
    List<Permission> listGrantable();

    /** Soft delete. Refused for a system permission, and for one still granted anywhere. */
    void delete(UUID id);

    /**
     * @param module      the functional area; the code is derived as {@code MODULE_ACTION}
     * @param action      what may be done
     * @param resource    URL spelling, e.g. {@code shipments}; defaults from the module
     */
    record CreatePermissionCommand(PermissionModule module,
                                   PermissionAction action,
                                   String permissionName,
                                   String resource,
                                   String description,
                                   Integer displayOrder,
                                   String requiredFeatureFlag) {
    }

    /**
     * Module and action are absent: they form the immutable code. Only the presentation
     * and gating of an existing permission may change.
     *
     * @param expectedVersion the version last read; stale values are rejected with 409
     */
    record UpdatePermissionCommand(String permissionName,
                                   String resource,
                                   String description,
                                   Integer displayOrder,
                                   String requiredFeatureFlag,
                                   Long expectedVersion) {
    }
}
