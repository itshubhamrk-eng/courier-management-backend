package com.courier.modules.finance.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Razorpay settings, bound from {@code app.payment.razorpay.*}.
 *
 * <p>{@code enabled} is off by default and there is no default secret. A deployment that
 * has not configured a gateway gets {@code UnconfiguredPaymentGateway}, which refuses every
 * online recharge with a clear 422 — the same fail-closed choice the rest of the project
 * makes. The alternative, a "skip verification in dev" flag, is exactly the switch that
 * eventually ships to production.
 *
 * <p>{@code keySecret} signs and verifies payments. It is env-only, never logged, and never
 * returned to a client; only {@code keyId} is publishable.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.razorpay")
public class RazorpayProperties {

    private boolean enabled = false;

    /** Publishable key id, handed to the browser checkout. */
    private String keyId;

    /** Signing secret. Env-only. */
    private String keySecret;

    private String apiBaseUrl = "https://api.razorpay.com/v1";

    /** Shown on the checkout dialog. */
    private String merchantName = "Courier SaaS";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(15);
}
