package com.courier.modules.communication.application.provider;

/**
 * "Use existing email infrastructure if available" — none does (auth's own {@code
 * NotificationPort}/{@code LogOnlyNotificationSender} confirms no SMTP is wired anywhere in
 * this codebase yet). {@code SmtpEmailProvider} uses a platform-level {@code JavaMailSender}
 * (env-configured {@code spring.mail.*}, the same "no default, fails only when actually
 * asked to send" posture {@code SecretsEncryptionProperties} already takes) — a company only
 * sets its own {@code fromName}/{@code fromEmail} identity, not SMTP credentials, since SMTP
 * itself is platform infrastructure here, not a per-company secret. {@code
 * LogOnlyEmailProvider} is the default when {@code spring.mail.host} isn't set.
 */
public interface EmailProvider {

    ProviderSendResult send(EmailMessage message, EmailIdentity identity);

    record EmailMessage(
            String toEmail,
            String subject,
            /** HTML body — templates may contain markup, per the brief's own "Support HTML
             *  templates". */
            String htmlBody
    ) {
    }

    record EmailIdentity(String fromName, String fromEmail) {
    }
}
