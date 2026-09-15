package com.courier.modules.company.domain;

/**
 * Whether a department may currently be placed on a new user.
 *
 * <p>Same shape as {@link RoleStatus} and for the same reason: deactivating a department
 * withdraws it from the picker without touching the users already in it or the roles it
 * grants them.
 */
public enum DepartmentStatus {

    /** Assignable to a user. */
    ACTIVE,

    /** Withdrawn from the assignment list. Existing holders are unaffected. */
    INACTIVE;

    public boolean isAssignable() {
        return this == ACTIVE;
    }
}
