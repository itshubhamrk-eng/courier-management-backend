package com.courier.modules.security.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Security incident detection settings, bound from {@code courier.security.*}.
 * Per-rule thresholds are added by the scanner task.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "courier.security")
public class SecurityProperties {

    /** Seconds a flagged session keeps working before enforcement. */
    private int graceSeconds = 10;

    /** How often the incident scanner runs. */
    private Duration scanInterval = Duration.ofMinutes(2);

    private Geo geo = new Geo();

    @Getter
    @Setter
    public static class Geo {
        /** Off by default; only prod turns it on. */
        private boolean enabled = false;

        /** Path to a MaxMind GeoLite2-Country .mmdb file. Blank means no database. */
        private String dbPath = "";

        /** ISO 3166-1 alpha-2 code treated as home. */
        private String allowedCountry = "IN";
    }
}
