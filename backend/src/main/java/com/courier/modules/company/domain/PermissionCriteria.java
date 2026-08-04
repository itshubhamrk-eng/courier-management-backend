package com.courier.modules.company.domain;

import java.util.Set;

/**
 * Filter criteria for a catalogue search. Every field is optional; null means
 * "do not constrain".
 *
 * @param modules          match any of these modules
 * @param actions          match any of these actions
 * @param status           ACTIVE or INACTIVE
 * @param systemPermission true for seeded, false for custom
 * @param resource         exact, case-insensitive
 * @param planGatedOnly    only permissions that depend on a subscription feature
 * @param search           free text over code, name, description and resource
 */
public record PermissionCriteria(
        Set<PermissionModule> modules,
        Set<PermissionAction> actions,
        PermissionStatus status,
        Boolean systemPermission,
        String resource,
        Boolean planGatedOnly,
        String search
) {

    public static PermissionCriteria none() {
        return new PermissionCriteria(null, null, null, null, null, null, null);
    }
}
