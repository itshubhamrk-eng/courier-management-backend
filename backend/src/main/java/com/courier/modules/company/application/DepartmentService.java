package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateDepartmentCommand;
import com.courier.modules.company.application.command.UpdateDepartmentCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.Department;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use cases for company departments.
 *
 * <p>Same two-audience shape as {@code RoleService}: {@code COMPANY_ADMIN} manages their
 * own company's departments, a {@code BRANCH_MANAGER} may only read the assignable list
 * (they place their own new hires into one), and {@code SUPER_ADMIN} may read across every
 * company for support. Enforced per method on {@code DepartmentServiceImpl}.
 */
public interface DepartmentService {

    Department create(CreateDepartmentCommand command);

    /** Full replacement of the editable fields, including the role grants. */
    Department update(UUID id, UpdateDepartmentCommand command);

    Department getById(UUID id);

    /** Every department of the current company, ordered by name. A company has a
     *  handful, not a catalogue — unpaged, like {@code RoleService.listAssignable}. */
    List<Department> listAll();

    /** ACTIVE departments only, for the department picker on user creation. */
    List<Department> listAssignable();

    /** The roles one department currently offers, ordered by role code. */
    List<CompanyRole> rolesOf(UUID departmentId);

    /** The roles offered by several departments at once, batched to avoid N+1. */
    Map<UUID, List<CompanyRole>> rolesOf(List<UUID> departmentIds);

    Department activate(UUID id);

    Department deactivate(UUID id);

    /** Soft delete. Refused while any user still holds this department. */
    void delete(UUID id);
}
