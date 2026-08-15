package com.courier.modules.company.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Geocoding settings, bound from {@code app.geocoding.*}. On by default — the provider is
 * Nominatim (OpenStreetMap), free and keyless, so unlike S3/Razorpay there is no secret
 * that forces an off-by-default.
 *
 * <p>{@code userAgent} exists because Nominatim's usage policy requires a real identifying
 * value on every request (no key to authenticate with instead); a deployment sending
 * heavier traffic than the public instance tolerates should point {@code baseUrl} at a
 * self-hosted or paid instance instead of raising this app's own request rate.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.geocoding")
public class GeocodingProperties {

    private boolean enabled = true;

    private String baseUrl = "https://nominatim.openstreetmap.org";

    private String userAgent = "courier-management-app (contact: set app.geocoding.user-agent)";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(5);
}
