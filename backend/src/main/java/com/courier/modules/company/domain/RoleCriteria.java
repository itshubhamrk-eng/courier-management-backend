package com.courier.modules.company.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a role search. Every field is optional; null means "do not
 * constrain on this".
 *
 * <p>In {@code domain} so the controller (which builds it) and the service (which hands
 * it to {@link RoleSpecifications}) share it without either depending on the other.
 *
 * @param companyId    restrict to one company. Ignored for a {@code COMPANY_ADMIN}, whose
 *                    company is already bound by the Hibernate filter; meaningful only
 *                    for a {@code SUPER_ADMIN} narrowing a platform-wide listing
 * @param status      ACTIVE or INACTIVE
 * @param roleTypes   match any of these functional groupings
 * @param systemRole  true for the seeded roles, false for a company's own
 * @param defaultRole true for the role new users get by default
 * @param permissionCode roles granting this specific permission, e.g. SHIPMENT_CANCEL
 * @param search      free text over code, name and description
 */
public record RoleCriteria(
        UUID companyId,
        RoleStatus status,
        Set<RoleType> roleTypes,
        Boolean systemRole,
        Boolean defaultRole,
        String permissionCode,
        String search
) {

    public static RoleCriteria none() {
        return new RoleCriteria(null, null, null, null, null, null, null);
    }

    /** The same criteria pinned to one company. */
    public RoleCriteria withCompanyId(UUID enforcedCompanyId) {
        return new RoleCriteria(enforcedCompanyId, status, roleTypes, systemRole, defaultRole,
                permissionCode, search);
    }
}
