package com.courier.modules.auth.domain;

/**
 * Why a login attempt failed. Recorded in {@code login_history} for forensics.
 *
 * <p>These values are internal. The API collapses several of them into a single
 * {@code INVALID_CREDENTIALS} response so the caller cannot tell an unknown email
 * from a wrong password.
 */
public enum LoginFailureReason {

    /** Email matched no user in this company. Reported to the caller as bad credentials. */
    UNKNOWN_USER,

    /** Password did not verify. Reported identically to UNKNOWN_USER. */
    BAD_PASSWORD,

    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    EMAIL_NOT_VERIFIED,
    PASSWORD_EXPIRED,
    COMPANY_INACTIVE,

    /** Too many attempts for this email+IP inside the throttle window. */
    THROTTLED
}
