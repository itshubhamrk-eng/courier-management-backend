package com.courier.modules.auth.domain;

import java.util.UUID;

/**
 * Failed logins ({@code login_history} LOGIN_FAILED rows) against one account (company + attempted
 * email) in a window, and from how many distinct IPs. Input to CREDENTIAL_ATTACK.
 */
public record LoginFailureAccountCount(UUID companyId, String attemptedEmail, long failures, long distinctIps) {
}
