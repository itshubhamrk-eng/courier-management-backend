package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final String defaultCountryCode;
    private final String templateLanguage;

    public MetaWhatsAppProvider(RestClient.Builder builder, String defaultCountryCode, String templateLanguage) {
        this.restClient = builder.baseUrl(GRAPH_API_BASE).build();
        this.defaultCountryCode = defaultCountryCode;
        this.templateLanguage = templateLanguage;
    }

    /**
     * Meta wants digits only, country code included, no leading {@code +} or trunk {@code 0}.
     * Customer phones are stored as typed ({@code 9123456780}, {@code 09123456780},
     * {@code +91 91234 56780}), so a bare national number gets {@code defaultCountryCode}.
     * A number already longer than a national one is taken to carry its own country code.
     */
    static String normalizeRecipient(String raw, String defaultCountryCode) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            return digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.length() <= 10 ? defaultCountryCode + digits : digits;
    }

    @Override
    public ProviderSendResult send(WhatsAppMessage message, WhatsAppCredentials credentials) {
        if (!credentials.isComplete()) {
            throw new ProviderSendException("WhatsApp is not configured for this company "
                    + "(missing Phone Number ID or Access Token).");
        }
        try {
            List<Map<String, Object>> components = new java.util.ArrayList<>();
            if (message.headerParameter() != null) {
                components.add(Map.of("type", "header", "parameters",
                        List.of(Map.of("type", "text", "text", message.headerParameter()))));
            }
            components.add(Map.of("type", "body", "parameters", message.templateParameters().stream()
                    .map(p -> Map.of("type", "text", "text", p)).toList()));
            for (ButtonParameter button : message.buttonParameters()) {
                components.add(Map.of("type", "button", "sub_type", "url", "index", String.valueOf(button.index()),
                        "parameters", List.of(Map.of("type", "text", "text", button.text()))));
            }
            String language = message.languageCode() == null || message.languageCode().isBlank()
                    ? templateLanguage : message.languageCode();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", normalizeRecipient(message.toE164(), defaultCountryCode));
            body.put("type", "template");
            body.put("template", Map.of(
                    "name", message.approvedTemplateName(),
                    "language", Map.of("code", language),
                    "components", components));

            Map<String, Object> response = parse(restClient.post()
                    .uri("/{phoneNumberId}/messages", credentials.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class));

            String messageId = extractMessageId(response);
            log.info("WhatsApp template '{}' sent via Meta Cloud API, message id {}",
                    message.approvedTemplateName(), messageId);
            return new ProviderSendResult(messageId);
        } catch (ProviderSendException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ProviderSendException("Meta WhatsApp Cloud API rejected the send: " + metaError(e), e);
        } catch (Exception e) {
            throw new ProviderSendException("Meta WhatsApp Cloud API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TemplateSubmissionResult submitTemplate(TemplateSubmission submission, WhatsAppCredentials credentials) {
        if (!credentials.hasBusinessAccount()) {
            throw new ProviderSendException("WhatsApp Business Account ID and Access Token are required "
                    + "to submit a template.");
        }
        try {
            List<Map<String, Object>> components = new java.util.ArrayList<>();
            if (submission.headerText() != null && !submission.headerText().isBlank()) {
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("type", "HEADER");
                header.put("format", "TEXT");
                header.put("text", submission.headerText());
                if (submission.headerExample() != null) {
                    header.put("example", Map.of("header_text", List.of(submission.headerExample())));
                }
                components.add(header);
            }
            Map<String, Object> bodyComponent = new LinkedHashMap<>();
            bodyComponent.put("type", "BODY");
            bodyComponent.put("text", submission.bodyText());
            if (!submission.bodyExamples().isEmpty()) {
                bodyComponent.put("example", Map.of("body_text", List.of(submission.bodyExamples())));
            }
            components.add(bodyComponent);
            if (submission.footerText() != null && !submission.footerText().isBlank()) {
                components.add(Map.of("type", "FOOTER", "text", submission.footerText()));
            }
            if (submission.buttons() != null && !submission.buttons().isEmpty()) {
                List<Map<String, Object>> buttons = new java.util.ArrayList<>();
                for (SubmissionButton b : submission.buttons()) {
                    Map<String, Object> button = new LinkedHashMap<>();
                    button.put("type", b.type());
                    button.put("text", b.text());
                    if ("URL".equals(b.type())) {
                        button.put("url", b.url());
                        if (b.urlExample() != null) {
                            button.put("example", List.of(b.urlExample()));
                        }
                    } else {
                        button.put("phone_number", b.phoneNumber());
                    }
                    buttons.add(button);
                }
                components.add(Map.of("type", "BUTTONS", "buttons", buttons));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", submission.name());
            body.put("language", submission.language());
            body.put("category", submission.category());
            body.put("components", components);

            Map<String, Object> response = parse(restClient.post()
                    .uri("/{wabaId}/message_templates", credentials.businessAccountId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class));

            String status = text(response, "status");
            log.info("WhatsApp template '{}' submitted to Meta: id={} status={}", submission.name(),
                    text(response, "id"), status);
            return new TemplateSubmissionResult(text(response, "id"), status == null ? "PENDING" : status,
                    text(response, "category") == null ? submission.category() : text(response, "category"));
        } catch (ProviderSendException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ProviderSendException("Meta rejected the template: " + metaError(e), e);
        } catch (Exception e) {
            throw new ProviderSendException("Meta template submission failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TemplateStatusResult fetchTemplateStatus(String name, String language, WhatsAppCredentials credentials) {
        if (!credentials.hasBusinessAccount()) {
            throw new ProviderSendException("WhatsApp Business Account ID and Access Token are required "
                    + "to check a template.");
        }
        try {
            Map<String, Object> response = parse(restClient.get()
                    .uri(uri -> uri.path("/{wabaId}/message_templates")
                            .queryParam("name", name)
                            .queryParam("fields", "id,name,status,category,language,rejected_reason")
                            .queryParam("limit", 50)
                            .build(credentials.businessAccountId()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .retrieve()
                    .body(String.class));
            Object data = response == null ? null : response.get("data");
            if (data instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> m && name.equals(m.get("name"))
                            && language.equalsIgnoreCase(String.valueOf(m.get("language")))) {
                        Map<String, Object> t = (Map<String, Object>) m;
                        String reason = text(t, "rejected_reason");
                        return new TemplateStatusResult(text(t, "id"), text(t, "status"), text(t, "category"),
                                reason == null || "NONE".equalsIgnoreCase(reason) ? null : reason);
                    }
                }
            }
            return new TemplateStatusResult(null, "NOT_FOUND", null, null);
        } catch (ProviderSendException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ProviderSendException("Meta template lookup failed: " + metaError(e), e);
        } catch (Exception e) {
            throw new ProviderSendException("Meta template lookup failed: " + e.getMessage(), e);
        }
    }

    /** Graph API sometimes labels a JSON body {@code text/javascript}, which Spring's JSON converter
     *  refuses — so every response is read as text and parsed here, whatever its content type. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(body, Map.class);
        } catch (Exception e) {
            throw new ProviderSendException("Meta returned a response that is not JSON: "
                    + (body.length() > 200 ? body.substring(0, 200) : body), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PricingReport fetchPricing(WhatsAppCredentials credentials, java.time.Instant from, java.time.Instant to) {
        if (!credentials.hasBusinessAccount()) {
            throw new ProviderSendException("WhatsApp Business Account ID and Access Token are required to read billing.");
        }
        String fields = "currency,pricing_analytics.start(" + from.getEpochSecond() + ").end(" + to.getEpochSecond()
                + ").granularity(DAILY).metric_types(COST,VOLUME).dimensions(PRICING_CATEGORY,PRICING_TYPE)";
        try {
            Map<String, Object> response = parse(restClient.get()
                    .uri(uri -> uri.path("/{wabaId}").queryParam("fields", fields).build(credentials.businessAccountId()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .retrieve()
                    .body(String.class));
            List<PricingPoint> points = new java.util.ArrayList<>();
            Object analytics = response == null ? null : response.get("pricing_analytics");
            if (analytics instanceof Map<?, ?> a && a.get("data") instanceof List<?> blocks) {
                for (Object block : blocks) {
                    if (block instanceof Map<?, ?> b && b.get("data_points") instanceof List<?> rows) {
                        for (Object row : rows) {
                            if (row instanceof Map<?, ?> r) {
                                points.add(new PricingPoint(str(r.get("pricing_category")), str(r.get("pricing_type")),
                                        number(r.get("volume")).longValue(),
                                        new java.math.BigDecimal(number(r.get("cost")).toString())));
                            }
                        }
                    }
                }
            }
            return new PricingReport(text(response, "currency"), points);
        } catch (ProviderSendException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ProviderSendException("Meta billing lookup failed: " + metaError(e), e);
        } catch (Exception e) {
            throw new ProviderSendException("Meta billing lookup failed: " + e.getMessage(), e);
        }
    }

    /** Meta's token and permission failures are the ones an admin can fix in a minute — say how. */
    static String hintFor(String code, String subcode) {
        return switch (code) {
            case "190" -> "Meta rejected the access token — it has expired or is no longer valid. Generate a new "
                    + "token in your Meta app (a System User token does not expire) and save it in Channel Settings.";
            case "10", "200" -> "The access token is missing a permission Meta needs for this. Generate the token "
                    + "with whatsapp_business_management and whatsapp_business_messaging, then save it in Channel Settings.";
            default -> "";
        };
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Number number(Object v) {
        return v instanceof Number n ? n : 0;
    }

    private static String text(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : v.toString();
    }

    /** Meta's own wording when it gave one ({@code error.error_user_msg}, else {@code
     *  error.message}) — far more useful to an admin than "400 Bad Request". */
    static String metaError(RestClientResponseException e) {
        try {
            JsonNode error = JSON.readTree(e.getResponseBodyAsString()).path("error");
            String user = error.path("error_user_msg").asText("");
            String message = error.path("message").asText("");
            String best = !user.isBlank() ? user : message;
            String code = error.path("code").asText("");
            String hint = hintFor(code, error.path("error_subcode").asText(""));
            if (!hint.isBlank()) {
                return hint + " (code " + code + ")";
            }
            if (!best.isBlank()) {
                return best + (code.isBlank() ? "" : " (code " + code + ")");
            }
        } catch (Exception ignored) {
            // fall through to the HTTP status line
        }
        return e.getStatusCode() + " " + e.getStatusText();
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
