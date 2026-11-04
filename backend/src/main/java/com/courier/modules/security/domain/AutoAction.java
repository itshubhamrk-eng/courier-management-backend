package com.courier.modules.security.domain;

/** What the system itself did when it raised the incident, before any human decided. */
public enum AutoAction {
    NONE, SESSIONS_REVOKED, USER_DISABLED
}
