package com.courier.modules.ewaybill.infrastructure;

import com.courier.modules.ewaybill.application.provider.EwayBillProvider;
import com.courier.modules.ewaybill.application.provider.UnconfiguredEwayBillProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the E-Way Bill provider for this deployment. Mirrors {@code PaymentGatewayConfig}
 * exactly: two mutually-exclusive, explicit conditions — enabling the real GSP
 * integration without a base URL/client id/secret fails at startup rather than at the
 * first booking that needs one.
 */
@Slf4j
@Configuration
public class EwayBillProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.ewaybill.gsp", name = "enabled", havingValue = "true")
    public EwayBillProvider ewayBillGspProvider(EwayBillGspProperties properties, RestClient.Builder builder) {
        if (isBlank(properties.getBaseUrl()) || isBlank(properties.getClientId())
                || isBlank(properties.getClientSecret())) {
            throw new IllegalStateException(
                    "app.ewaybill.gsp.enabled is true but base-url/client-id/client-secret are not set. "
                            + "Set EWAYBILL_GSP_BASE_URL, EWAYBILL_GSP_CLIENT_ID and EWAYBILL_GSP_CLIENT_SECRET, "
                            + "or disable the integration.");
        }
        log.info("E-Way Bill GSP provider enabled (base url {})", properties.getBaseUrl());
        return new EwayBillGspProvider(properties, builder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ewaybill.gsp", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public EwayBillProvider unconfiguredEwayBillProvider() {
        log.warn("No E-Way Bill provider configured — auto-generation will always be refused gracefully "
                + "(shipments/manifests are never blocked by this).");
        return new UnconfiguredEwayBillProvider();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
