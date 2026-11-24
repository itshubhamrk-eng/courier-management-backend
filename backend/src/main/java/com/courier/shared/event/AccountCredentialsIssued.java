package com.courier.shared.event;

import java.util.UUID;

/**
 * "A login account was just created and its password is known right now" — published by the
 * module that created it, consumed by Communication, which welcomes the person over WhatsApp.
 *
 * <p>Carries the one-time password in memory only: it is never persisted, audited or logged, and
 * {@link #toString()} masks it so an accidental {@code log.info(event)} cannot leak it. Only
 * published when the password is actually known — an account made with a generated, unusable
 * password (admin must reset it later) has nothing to send and publishes nothing.
 *
 * @param contact    the mobile number the welcome goes to; a blank one means nothing is sent
 * @param loginId    what the person types into the login screen's e-mail field
 * @param companyCode what the person types into the login screen's company field
 */
public record AccountCredentialsIssued(
        UUID companyId,
        UUID userId,
        String companyCode,
        String companyName,
        String displayName,
        String loginId,
        String contact,
        String password) {

    @Override
    public String toString() {
        return "AccountCredentialsIssued[companyId=" + companyId + ", userId=" + userId
                + ", loginId=" + loginId + ", password=***]";
    }
}
