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

    /**
     * Length of the sliding window each scan covers: {@code [to - scanWindow, to)}. Longer
     * than {@link #scanInterval} on purpose, so consecutive scans overlap and a rule that
     * failed (or a row that committed late) is picked up by the next run; incident
     * {@code raise} dedups, so overlap is safe. Every per-window threshold below is
     * counted over this window.
     */
    private Duration scanWindow = Duration.ofMinutes(10);

    /** Scan up to {@code now - scanLag}, so asynchronously written rows have committed. */
    private Duration scanLag = Duration.ofSeconds(30);

    /**
     * Bound for documentation only: the scheduler switch is read by
     * {@code @ConditionalOnProperty(courier.security.scan-enabled)} on SecurityScanJob
     * (default true); set it to false to disable scanning.
     */
    private boolean scanEnabled = true;

    /**
     * REVOKED_TOKEN_REPLAY fires when one user has MORE than this many replays of an
     * already-revoked refresh token (no rotation successor: logout/eviction/ban) inside one
     * scan window ({@link #scanWindow}). A well-behaved client stops after the first
     * rejection, so a couple of retries are tolerated; every replay also kills the token
     * family, so more than 3 in 10 minutes means something is hammering stale credentials.
     */
    private int revokedReplayThreshold = 3;

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
