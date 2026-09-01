package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Meta WhatsApp Cloud API, called directly — same "plain REST client, no vendor SDK" choice
 * {@code RazorpayPaymentGateway} made. One endpoint: {@code POST
 * /{phoneNumberId}/messages} with an approved template + positional body parameters (Meta
 * requires an approved template for any business-initiated conversation — free-form text is
 * only legal inside a customer-initiated 24h window, which this module has no way to know
 * it's inside, so every send here is template-shaped).
 *
 * <p>Never constructed with credentials baked in: unlike {@code RazorpayPaymentGateway}
 * (one platform-wide account), WhatsApp credentials are genuinely per-company, so they
 * travel on every {@link #send} call instead, decrypted by the caller
 * ({@code CommunicationSendService}) only immediately before this method runs.
 */
@Slf4j
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private final RestClient restClient;

    public MetaWhatsAppProvider(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(GRAPH_API_BASE).build();
    }

    @Override
    public ProviderSendResult send(WhatsAppMessage message, WhatsAppCredentials credentials) {
        if (!credentials.isComplete()) {
            throw new ProviderSendException("WhatsApp is not configured for this company "
                    + "(missing Phone Number ID or Access Token).");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", message.toE164());
            body.put("type", "template");
            body.put("template", Map.of(
                    "name", message.approvedTemplateName(),
                    "language", Map.of("code", "en"),
                    "components", List.of(Map.of(
                            "type", "body",
                            "parameters", message.templateParameters().stream()
                                    .map(p -> Map.of("type", "text", "text", p))
                                    .toList()))));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/{phoneNumberId}/messages", credentials.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String messageId = extractMessageId(response);
            log.info("WhatsApp template '{}' sent via Meta Cloud API, message id {}",
                    message.approvedTemplateName(), messageId);
            return new ProviderSendResult(messageId);
        } catch (ProviderSendException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderSendException("Meta WhatsApp Cloud API call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractMessageId(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object messages = response.get("messages");
        if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object id = first.get("id");
            return id == null ? null : id.toString();
        }
        return null;
    }
}
