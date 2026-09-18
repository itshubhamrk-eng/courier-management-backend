package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.RazorpayMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Never carries either key secret itself — only whether each one is set. Same rule
 * {@code RazorpayProperties} states for the platform-wide key: a secret signs and verifies
 * payments and must never reach a client once written.
 */
@Schema(name = "CompanyRazorpayConfigResponse")
public record CompanyRazorpayConfigResponse(
        boolean enabled,
        RazorpayMode mode,
        String testKeyId,
        @Schema(description = "Whether a test key secret has ever been saved — never the secret itself.")
        boolean testKeySecretConfigured,
        String liveKeyId,
        @Schema(description = "Whether a live key secret has ever been saved — never the secret itself.")
        boolean liveKeySecretConfigured,
        Instant updatedAt
) {
}
