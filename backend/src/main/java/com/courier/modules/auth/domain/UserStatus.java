package com.courier.modules.auth.domain;

/**
 * Account lifecycle.
 *
 * <p>Distinct from the transient {@code lockedUntil} field: a lock is temporary and
 * self-clearing, whereas {@link #DISABLED} is an administrative decision that only
 * an administrator can reverse.
 */
public enum UserStatus {

    /** Created but the email address has not been confirmed yet. Cannot log in. */
    PENDING,

    /** Normal, usable account. */
    ACTIVE,

    /**
     * Administratively locked. Note this is *not* the failed-login lock — that is
     * {@code lockedUntil}, which expires on its own.
     */
    LOCKED,

    /** Deactivated. Cannot log in; retained for audit and referential integrity. */
    DISABLED;

    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING;
    }
}
