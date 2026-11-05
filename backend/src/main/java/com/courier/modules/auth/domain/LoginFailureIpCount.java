package com.courier.modules.auth.domain;

import java.util.UUID;

/**
 * Failed logins ({@code login_history} LOGIN_FAILED rows) from one IP against one company in a
 * window: how many distinct attempted emails, and how many attempts. Input to CREDENTIAL_ATTACK.
 */
public record LoginFailureIpCount(String ipAddress, UUID companyId, long distinctEmails, long failures) {
}
