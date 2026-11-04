package com.courier.modules.security.domain;

/**
 * The detection rules that can raise a {@link SecurityIncident}. The first three are
 * strong enough evidence of account compromise that the system acts on its own
 * ({@link #autoBan()}); the rest only raise an incident for a human to decide.
 */
public enum IncidentRule {

    REFRESH_TOKEN_REUSE(true),
    FOREIGN_LOGIN_CORRECT_PASSWORD(true),
    FOREIGN_TOKEN_USE(true),
    REVOKED_TOKEN_REPLAY(false),
    SESSION_AGENT_CHANGE(false),
    SESSION_MULTI_IP(false),
    BULK_ACTIVITY(false),
    ACCESS_DENIED_BURST(false),
    CREDENTIAL_ATTACK(false),
    FOREIGN_LOGIN_ATTEMPT(false);

    private final boolean autoBan;

    IncidentRule(boolean autoBan) {
        this.autoBan = autoBan;
    }

    /** True when detecting this rule revokes sessions / disables the user without waiting for a human. */
    public boolean autoBan() {
        return autoBan;
    }
}
