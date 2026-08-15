package com.courier.modules.company.infrastructure;

import com.courier.modules.company.application.geocoding.GeocodingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the geocoding backend for this deployment. Same two-explicit-conditions shape as
 * {@code PaymentGatewayConfig}/{@code FileStorageConfig}, except both sides are harmless:
 * geocoding is a best-effort enrichment, never a hard requirement to create a branch.
 */
@Slf4j
@Configuration
public class GeocodingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.geocoding", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public GeocodingPort nominatimGeocodingService(GeocodingProperties properties,
                                                    RestClient.Builder restClientBuilder) {
        log.info("Geocoding enabled ({})", properties.getBaseUrl());
        return new NominatimGeocodingService(properties, restClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.geocoding", name = "enabled", havingValue = "false")
    public GeocodingPort noopGeocodingService() {
        log.warn("Geocoding disabled — a branch created without latitude/longitude keeps them null.");
        return new NoopGeocodingService();
    }
}
