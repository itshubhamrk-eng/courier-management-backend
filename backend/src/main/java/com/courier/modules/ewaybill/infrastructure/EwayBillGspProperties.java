package com.courier.modules.ewaybill.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for a real government/GSP E-Way Bill integration, bound from
 * {@code app.ewaybill.gsp.*}. All env-only, no defaults for any credential — a
 * deployment without them gets {@code UnconfiguredEwayBillProvider}, which refuses
 * generation gracefully (never a fake success) exactly like {@code RazorpayProperties}'
 * own honesty note.
 *
 * <p>{@code base-url} and the four path fragments are configuration, not a hardcoded
 * constant, because different GSP vendors expose different hosts and route shapes —
 * this deployment has none configured yet, so no real host is baked in anywhere.
 * {@code clientSecret}/{@code password} are never logged and never returned to a
 * client — see {@code EwayBillGspProvider}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ewaybill.gsp")
public class EwayBillGspProperties {

    private boolean enabled = false;

    private String baseUrl;

    /** GSP/API client id — publishable-ish, but still env-only since no default exists. */
    private String clientId;

    /** GSP/API client secret. Env-only, never logged. */
    private String clientSecret;

    /** The GST-portal username the GSP account is registered against, where the vendor's
     *  auth flow needs one alongside the client id/secret. Optional — blank when not. */
    private String username;

    /** Env-only, never logged. Optional — blank when the vendor's auth flow needs only
     *  {@link #clientId}/{@link #clientSecret}. */
    private String password;

    /** This company's own GSTIN, sent as the requesting taxpayer on every call. */
    private String gstin;

    private String partAPath = "/ewayapi/partA";

    private String partBPath = "/ewayapi/partB";

    private String statusPath = "/ewayapi/status";

    private String cancelPath = "/ewayapi/cancel";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(15);
}
