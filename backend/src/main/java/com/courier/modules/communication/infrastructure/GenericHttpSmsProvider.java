package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.SmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * "Create provider abstraction... do not hardcode provider" taken literally: this POSTs a
 * generic JSON envelope ({@code to}/{@code message}/{@code senderId}) to whatever {@code
 * apiUrl} a company configures, with {@code apiKey} as a Bearer token — the shape most SMS
 * aggregators' simple HTTP APIs already follow closely enough to be a real integration
 * point, without coupling this codebase to one named vendor's own request format/SDK.
 */
@Slf4j
public class GenericHttpSmsProvider implements SmsProvider {

    private final RestClient restClient;

    public GenericHttpSmsProvider(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public ProviderSendResult send(SmsMessage message, SmsCredentials credentials) {
        if (!credentials.isComplete()) {
            throw new ProviderSendException("SMS is not configured for this company "
                    + "(missing API URL or API key).");
        }
        try {
            Map<String, Object> body = Map.of(
                    "to", message.toE164(),
                    "message", message.body(),
                    "senderId", credentials.senderId() == null ? "" : credentials.senderId());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(credentials.apiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.apiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String messageId = response == null || response.get("messageId") == null
                    ? null : response.get("messageId").toString();
            log.info("SMS sent via {} ({}), message id {}", credentials.providerName(), credentials.apiUrl(),
                    messageId);
            return new ProviderSendResult(messageId);
        } catch (ProviderSendException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderSendException("SMS gateway call failed: " + e.getMessage(), e);
        }
    }
}
