package com.courier.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS settings, bound from {@code app.cors.*}.
 *
 * <p>Origins are an explicit allowlist per environment. A wildcard origin combined
 * with {@code allowCredentials} is rejected by the browser anyway, and a wildcard
 * without it still lets any site read responses — so production must list real hosts.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:5173");

    private boolean allowCredentials = false;
}
