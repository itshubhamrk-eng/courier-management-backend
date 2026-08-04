package com.courier.modules.company.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a branch search. Every field optional; null means "do not
 * constrain". Lives in {@code domain} so controller and service share it.
 *
 * @param companyId    restrict to one company — meaningful only for a SUPER_ADMIN; for a
 *                    COMPANY_ADMIN it is overridden to their own
 * @param branchTypes match any of these types
 * @param statuses    match any of these statuses
 * @param managerId   branches managed by this user
 * @param city        exact, case-insensitive
 * @param state       exact, case-insensitive
 * @param postalCode  exact
 * @param allowBooking / allowDelivery / allowPickup   capability toggles
 * @param branchIds   restrict to these ids — used to scope a non-admin to their branch
 * @param search      free text over code, name, email, city and postal code
 */
public record BranchCriteria(
        UUID companyId,
        Set<BranchType> branchTypes,
        Set<BranchStatus> statuses,
        UUID managerId,
        String city,
        String state,
        String postalCode,
        Boolean allowBooking,
        Boolean allowDelivery,
        Boolean allowPickup,
        Set<UUID> branchIds,
        String search
) {

    public static BranchCriteria none() {
        return new BranchCriteria(null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public BranchCriteria withCompanyId(UUID enforced) {
        return new BranchCriteria(enforced, branchTypes, statuses, managerId, city, state,
                postalCode, allowBooking, allowDelivery, allowPickup, branchIds, search);
    }

    /** Pin the result to a specific set of branch ids — a non-admin's visible branches. */
    public BranchCriteria withBranchIds(Set<UUID> enforced) {
        return new BranchCriteria(companyId, branchTypes, statuses, managerId, city, state,
                postalCode, allowBooking, allowDelivery, allowPickup, enforced, search);
    }
}
