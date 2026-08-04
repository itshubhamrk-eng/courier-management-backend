package com.courier.modules.company.domain;

/**
 * Whether a permission may be granted to roles.
 *
 * <p>Deactivating retires a right without deleting it: existing grants survive, so a
 * permission can be withdrawn from the catalogue while the roles that already hold it
 * keep working until they are migrated. Deleting would strip access the moment it ran.
 */
public enum PermissionStatus {

    /** Grantable. */
    ACTIVE,

    /** Retired from the catalogue. Existing grants are untouched. */
    INACTIVE;

    public boolean isGrantable() {
        return this == ACTIVE;
    }
}
