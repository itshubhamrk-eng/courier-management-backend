package com.courier.modules.auth.domain;

/**
 * What a {@code login_history} row represents. Added in V86 on top of the original
 * success/failure boolean, so the table also carries the session's end — a login
 * session's whole lifecycle is now this event stream, not two separate tables.
 */
public enum LoginEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    SESSION_EXPIRED
}
