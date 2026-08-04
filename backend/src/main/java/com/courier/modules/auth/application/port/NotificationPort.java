package com.courier.modules.auth.application.port;

/**
 * Delivery of the transactional emails authentication needs.
 *
 * <p>Abstracted so the module is testable without an SMTP server, and so the
 * provider choice — still open, see {@code MEMORY/modules/auth.md} — does not leak
 * into the use cases.
 *
 * <p>Implementations must not throw on delivery failure in a way that breaks the
 * caller: a bounced email must not roll back a completed password change.
 */
public interface NotificationPort {

    /**
     * @param link fully-formed URL including the raw token. The raw token exists
     *             only here and in the user's inbox — it is never persisted or
     *             logged in production.
     */
    void sendPasswordResetLink(String email, String displayName, String link);

    void sendEmailVerificationLink(String email, String displayName, String link);

    /**
     * The activation email a company's first administrator receives when a super admin
     * creates the company.
     *
     * <p>Carries the activation link and the company's name — never the temporary
     * password. That is returned once to the super admin who created the company; an
     * email is the wrong place for a credential, and this address has not yet been
     * proven to belong to anyone.
     *
     * @param companyName shown in the email so the recipient knows which of possibly
     *                    several accounts is being activated
     */
    void sendCompanyActivation(String email, String displayName, String companyName, String link);
}
