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

    /**
     * Submits a message template to Meta for approval ({@code POST /{wabaId}/message_templates}).
     * Approval itself is Meta's review — this only files it.
     *
     * @throws ProviderSendException on any failure, with Meta's own reason when it gave one
     */
    TemplateSubmissionResult submitTemplate(TemplateSubmission submission, WhatsAppCredentials credentials);

    /** Asks Meta where a template stands. {@code status} is {@code NOT_FOUND} when Meta has no
     *  template of that name and language. */
    TemplateStatusResult fetchTemplateStatus(String name, String language, WhatsAppCredentials credentials);

    /**
     * What Meta has actually billed this WhatsApp Business Account between {@code from} and {@code to}
     * ({@code pricing_analytics}): delivered-message volume and approximate cost per pricing category.
     * Meta publishes no rate card through its API; this is the only way to learn the real price paid.
     *
     * @throws ProviderSendException on any failure, with Meta's own reason (a missing permission is the usual one)
     */
    PricingReport fetchPricing(WhatsAppCredentials credentials, java.time.Instant from, java.time.Instant to);

    /** @param currency the account's billing currency (null if Meta did not say) */
    record PricingReport(String currency, java.util.List<PricingPoint> points) {
    }

    /** One slice of Meta's billing data. {@code cost} is "approximate" in Meta's own words. */
    record PricingPoint(String category, String pricingType, long volume, java.math.BigDecimal cost) {
    }

    record TemplateSubmission(
            String name,
            String language,
            /** UTILITY, MARKETING or AUTHENTICATION. */
            String category,
            /** Body with Meta's positional {{1}}..{{n}} placeholders already in place. */
            String bodyText,
            /** One sample per placeholder, in order — Meta refuses a template without them. */
            java.util.List<String> bodyExamples,
            /** Optional TEXT header with at most one {{1}}; null for none. */
            String headerText,
            /** Sample for the header's variable; null when it has none. */
            String headerExample,
            /** Optional footer line; null for none. */
            String footerText,
            java.util.List<SubmissionButton> buttons
    ) {
        /** Body only — the original shape. */
        public TemplateSubmission(String name, String language, String category, String bodyText,
                                  java.util.List<String> bodyExamples) {
            this(name, language, category, bodyText, bodyExamples, null, null, null, java.util.List.of());
        }
    }

    /** {@code url} may end in {{1}} with {@code urlExample} the full sample URL; call buttons use {@code phoneNumber}. */
    record SubmissionButton(String type, String text, String url, String urlExample, String phoneNumber) {
    }

    record TemplateSubmissionResult(String metaTemplateId, String status, String category) {
    }

    record TemplateStatusResult(String metaTemplateId, String status, String category, String rejectedReason) {
    }

    record WhatsAppMessage(
            String toE164,
            /** The Meta-approved template name — {@code CommunicationTemplate.templateName}. */
            String approvedTemplateName,
            /** Positional template variables, in the order the approved template declares. */
            java.util.List<String> templateParameters,
            /** The language the template was approved in; null = the deployment default. */
            String languageCode,
            /** Value for the header's single variable; null when the header has none. */
            String headerParameter,
            /** Values for URL buttons that end in a variable. */
            java.util.List<ButtonParameter> buttonParameters
    ) {
        /** Body parameters only, deployment-default language — the original shape. */
        public WhatsAppMessage(String toE164, String approvedTemplateName, java.util.List<String> templateParameters) {
            this(toE164, approvedTemplateName, templateParameters, null, null, java.util.List.of());
        }
    }

    /** @param index the button's position (0-based) among all the template's buttons */
    record ButtonParameter(int index, String text) {
    }

    record WhatsAppCredentials(
            String phoneNumberId,
            String businessAccountId,
            String accessToken
    ) {
        /** Template management goes through the WhatsApp Business Account, not the phone number. */
        public boolean hasBusinessAccount() {
            return businessAccountId != null && !businessAccountId.isBlank()
                    && accessToken != null && !accessToken.isBlank();
        }

        public boolean isComplete() {
            return phoneNumberId != null && !phoneNumberId.isBlank()
                    && accessToken != null && !accessToken.isBlank();
        }
    }
}
