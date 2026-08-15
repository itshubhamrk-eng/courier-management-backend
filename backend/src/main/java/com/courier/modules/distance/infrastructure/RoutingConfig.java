package com.courier.modules.distance.infrastructure;

import com.courier.modules.distance.application.routing.RoutingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Picks the routing backend for this deployment. Same shape as {@code GeocodingConfig}. */
@Slf4j
@Configuration
public class RoutingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.routing", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RoutingPort osrmRoutingService(RoutingProperties properties,
                                          RestClient.Builder restClientBuilder) {
        log.info("Routing enabled ({})", properties.getBaseUrl());
        return new OsrmRoutingService(properties, restClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.routing", name = "enabled", havingValue = "false")
    public RoutingPort noopRoutingService() {
        log.warn("Routing disabled — address-to-address distance cannot be resolved.");
        return new NoopRoutingService();
    }
}
