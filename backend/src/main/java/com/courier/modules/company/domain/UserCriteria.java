package com.courier.modules.company.domain;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a user search. Every field optional; null means "do not constrain".
 *
 * <p>In {@code domain} so the controller (which builds it) and the service (which hands
 * it to {@link UserSpecifications}) share it without either depending on the other.
 *
 * @param companyId     restrict to one company — only meaningful for a SUPER_ADMIN
 *                     narrowing a platform listing; for a COMPANY_ADMIN it is overridden
 * @param statuses     match any of these statuses
 * @param locked       the admin hard-lock flag
 * @param branchId     users placed at this branch
 * @param hubId        users placed at this hub
 * @param department   exact, case-insensitive
 * @param designation  exact, case-insensitive
 * @param roleCode     users holding this company role
 * @param joinedFrom   joining date on or after
 * @param joinedTo     joining date on or before
 * @param search       free text over name, email, username, employee code and mobile
 */
public record UserCriteria(
        UUID companyId,
        Set<UserStatus> statuses,
        Boolean locked,
        UUID branchId,
        UUID hubId,
        String department,
        String designation,
        String roleCode,
        LocalDate joinedFrom,
        LocalDate joinedTo,
        String search
) {

    public static UserCriteria none() {
        return new UserCriteria(null, null, null, null, null, null, null, null, null, null, null);
    }

    /** The same criteria pinned to one company. */
    public UserCriteria withCompanyId(UUID enforced) {
        return new UserCriteria(enforced, statuses, locked, branchId, hubId, department,
                designation, roleCode, joinedFrom, joinedTo, search);
    }

    /** The same criteria pinned to one branch — for a branch manager's scoped view. */
    public UserCriteria withBranchId(UUID enforced) {
        return new UserCriteria(companyId, statuses, locked, enforced, hubId, department,
                designation, roleCode, joinedFrom, joinedTo, search);
    }

    /** The same criteria pinned to one hub — for a hub manager's scoped view. */
    public UserCriteria withHubId(UUID enforced) {
        return new UserCriteria(companyId, statuses, locked, branchId, enforced, department,
                designation, roleCode, joinedFrom, joinedTo, search);
    }
}
