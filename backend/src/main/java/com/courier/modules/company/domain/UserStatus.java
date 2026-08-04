package com.courier.modules.company.domain;

/**
 * Account state, from the company-administration point of view.
 *
 * <p><b>The values match {@code auth.UserStatus} exactly on purpose.</b> This enum and
 * auth's map the same {@code users.status} column from two bounded contexts — auth
 * decides whether a login is allowed, company administers the account. If the two
 * disagreed on a constant name, a status written by one would be unreadable by the other,
 * and worse, a value auth does not recognise as "cannot log in" would let a deactivated
 * user authenticate. So {@link #operational()} mirrors auth's login gate.
 */
public enum UserStatus {

    /** Created but not yet usable — e.g. awaiting email verification. Cannot log in. */
    PENDING,

    /** Normal, usable account. */
    ACTIVE,

    /** Administratively locked (or auto-locked by failed logins). Cannot log in. */
    LOCKED,

    /** Deactivated by an admin. Cannot log in; distinct from LOCKED, which is punitive. */
    DISABLED;

    /** Whether a user in this status may authenticate — must equal auth's own gate. */
    public boolean operational() {
        return this == ACTIVE;
    }
}
