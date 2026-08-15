package com.courier.modules.distance.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Routing settings, bound from {@code app.routing.*}. On by default — the provider is
 * OSRM's public demo server, free and keyless, same reasoning {@code GeocodingProperties}
 * gives for defaulting on.
 *
 * <p>The demo server is rate-limited and explicitly not for production traffic; a
 * deployment with real volume should point {@code baseUrl} at a self-hosted OSRM instance
 * instead of raising this app's own request rate against the public one.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {

    private boolean enabled = true;

    private String baseUrl = "https://router.project-osrm.org";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(8);
}
