package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateRoleCommand;
import com.courier.modules.company.application.command.UpdateRoleCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.RoleCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for company roles.
 *
 * <p><b>Two different audiences, deliberately different rules:</b>
 *
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — full management, but only inside their own company. The
 *       company comes from their verified JWT and is never accepted from a request.</li>
 *   <li>{@code SUPER_ADMIN} — read-only, across every company. A platform admin
 *       inspecting a support ticket needs to see a company's roles; nobody outside a
 *       company should be able to change what its staff can do.</li>
 * </ul>
 *
 * <p>Enforced with per-method {@code @PreAuthorize} on {@link RoleServiceImpl} rather
 * than at class level, because the read and write rules genuinely differ here.
 *
 * <p>Returns entities, not DTOs; the wire contract belongs to the {@code api} layer.
 */
public interface RoleService {

    CompanyRole create(CreateRoleCommand command);

    /** Full replacement of the editable fields. Fails with 409 on a stale version. */
    CompanyRole update(UUID id, UpdateRoleCommand command);

    CompanyRole getById(UUID id);

    Page<CompanyRole> search(RoleCriteria criteria, Pageable pageable);

    /** Assignable roles of the current company, for a role picker. */
    List<CompanyRole> listAssignable();

    CompanyRole activate(UUID id);

    CompanyRole deactivate(UUID id);

    /**
     * Soft delete. Refused for a system role — a company that deleted
     * {@code COMPANY_ADMIN} would have nobody able to administer it.
     */
    void delete(UUID id);
}
