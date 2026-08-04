package com.courier.modules.company.domain;

/**
 * Whether a role may currently be assigned to users.
 *
 * <p>Replaces the boolean {@code is_active} the table carried in {@code V4}. A boolean
 * answers "on or off" and nothing else; a status can grow a third value — an archived or
 * pending-approval role — without another migration and without a second flag that can
 * contradict the first.
 *
 * <p>Deactivating is not deleting: existing holders keep the role and their access,
 * exactly as a deactivated subscription plan grandfathers its subscribers. It only stops
 * the role being handed to anyone new.
 */
public enum RoleStatus {

    /** Assignable. */
    ACTIVE,

    /** Withdrawn from the assignment list. Existing holders are unaffected. */
    INACTIVE;

    public boolean isAssignable() {
        return this == ACTIVE;
    }
}
