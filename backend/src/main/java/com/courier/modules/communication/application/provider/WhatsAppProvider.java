package com.courier.modules.communication.application.provider;

/**
 * Meta WhatsApp Cloud API, called directly (no vendor SDK — same "plain REST client" choice
 * {@code RazorpayPaymentGateway} already made for its own vendor). {@code
 * MetaWhatsAppProvider} is the real implementation; {@code LogOnlyWhatsAppProvider} — the
 * default, since no dev-environment Meta Business account exists — just logs and returns a
 * synthetic message id, the same accepted-gap class {@code LogOnlyNotificationSender}
 * already is for auth's own emails.
 */
public interface WhatsAppProvider {

    /**
     * @param credentials this company's own Phone Number ID / Business Account ID /
     *                    Access Token, decrypted only here, at the point of use — never
     *                    logged, never returned by any API response
     * @throws ProviderSendException on any failure — network, vendor rejection, missing
     *                               credentials
     */
    ProviderSendResult send(WhatsAppMessage message, WhatsAppCredentials credentials);

    record WhatsAppMessage(
            String toE164,
            /** The Meta-approved template name — {@code CommunicationTemplate.templateName}. */
            String approvedTemplateName,
            /** Positional template variables, in the order the approved template declares. */
            java.util.List<String> templateParameters
    ) {
    }

    record WhatsAppCredentials(
            String phoneNumberId,
            String businessAccountId,
            String accessToken
    ) {
        public boolean isComplete() {
            return phoneNumberId != null && !phoneNumberId.isBlank()
                    && accessToken != null && !accessToken.isBlank();
        }
    }
}
