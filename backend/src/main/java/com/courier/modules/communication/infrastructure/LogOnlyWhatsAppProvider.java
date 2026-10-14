package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.shared.domain.TimeOrderedUuid;
import lombok.extern.slf4j.Slf4j;

/**
 * Default WhatsApp provider — logs instead of calling Meta, same accepted-gap class as
 * auth's own {@code LogOnlyNotificationSender}: no dev-environment Meta Business account
 * exists to call. Never throws — a message with incomplete credentials still gets a
 * synthetic id here, since the "not configured" refusal belongs to the setting/template
 * gate upstream in {@code CommunicationOrchestrator}, not to the provider itself.
 */
@Slf4j
public class LogOnlyWhatsAppProvider implements WhatsAppProvider {

    @Override
    public ProviderSendResult send(WhatsAppMessage message, WhatsAppCredentials credentials) {
        String id = "log-whatsapp-" + TimeOrderedUuid.generate();
        log.info("[LogOnlyWhatsAppProvider] to={} template={} params={} -> {}",
                mask(message.toE164()), message.approvedTemplateName(), message.templateParameters(), id);
        return new ProviderSendResult(id);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    @Override
    public TemplateSubmissionResult submitTemplate(TemplateSubmission submission, WhatsAppCredentials credentials) {
        log.info("[LogOnlyWhatsAppProvider] template submit name={} lang={} category={} body={}",
                submission.name(), submission.language(), submission.category(), submission.bodyText());
        return new TemplateSubmissionResult("log-template-" + java.util.UUID.randomUUID(), "PENDING",
                submission.category());
    }

    @Override
    public TemplateStatusResult fetchTemplateStatus(String name, String language, WhatsAppCredentials credentials) {
        // Never fabricates an approval: a log-only deployment never reached Meta, so there is
        // nothing to approve.
        return new TemplateStatusResult(null, "PENDING", null,
                "Log-only provider: nothing was sent to Meta. Set COMMUNICATION_WHATSAPP_ENABLED=true.");
    }

    @Override
    public PricingReport fetchPricing(WhatsAppCredentials credentials, java.time.Instant from, java.time.Instant to) {
        // Nothing was ever sent to Meta, so nothing was ever billed — an empty report, not an invented one.
        return new PricingReport(null, java.util.List.of());
    }
}
