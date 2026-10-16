package com.courier.modules.platformintegration.infrastructure;

import com.courier.modules.platformintegration.application.CoreAccessClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Calls the platform's service API ({@code GET /api/v1/service/access}) with this app's own client-credentials
 * token. The token is cached until shortly before it expires.
 */
@Component
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
class HttpCoreAccessClient implements CoreAccessClient {

    private final PlatformIntegrationProperties properties;
    private final RestClient http = RestClient.builder()
            .requestFactory(timeouts())
            .build();

    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    HttpCoreAccessClient(PlatformIntegrationProperties properties) {
        this.properties = properties;
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory timeouts() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }

    @Override
    public Decision check(UUID platformCompanyId, String userSubject) {
        try {
            JsonNode body = http.get()
                    .uri(properties.coreBaseUrl().replaceAll("/+$", "")
                            + "/api/v1/service/access?companyId={c}&userSubject={s}", platformCompanyId, userSubject)
                    .headers(h -> h.setBearerAuth(serviceToken()))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.has("allowed")) {
                throw new IllegalStateException("The platform returned an unexpected answer.");
            }
            Set<String> roles = new HashSet<>();
            body.path("roles").forEach(r -> roles.add(r.asText()));
            return new Decision(body.path("allowed").asBoolean(false), body.path("reason").asText("UNKNOWN"), roles);
        } catch (RestClientException e) {
            throw new IllegalStateException("The platform could not be reached: " + e.getMessage(), e);
        }
    }

    private synchronized String serviceToken() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return token;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.serviceClientId());
        form.add("client_secret", properties.serviceClientSecret());
        JsonNode response = http.post()
                .uri(properties.coreTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("The platform did not issue a service token.");
        }
        token = response.get("access_token").asText();
        tokenExpiresAt = Instant.now().plusSeconds(response.path("expires_in").asLong(60));
        return token;
    }
}
