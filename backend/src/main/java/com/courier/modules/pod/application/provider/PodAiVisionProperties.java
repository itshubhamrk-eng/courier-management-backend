package com.courier.modules.pod.application.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Vision-AI vendor settings, bound from {@code pod.ai.vision.*}. Only consulted when
 * {@code pod.ai.provider=vision} (see {@link VisionPodVerificationProvider}); a blank
 * {@link #apiKey} makes that provider fail closed exactly like an unconfigured
 * {@code PaymentGatewayPort}/{@code FileStoragePort} elsewhere in this codebase — the caller
 * gets routed to manual review, never a false PASS.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pod.ai.vision")
public class PodAiVisionProperties {

    /** Vendor API key. Env-only, never logged. */
    private String apiKey;

    private String baseUrl = "https://api.anthropic.com/v1";

    private String model = "claude-sonnet-4-5";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(20);
}
