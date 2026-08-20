package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Never carries the key secret itself — only whether one is set. Same rule
 * {@code RazorpayProperties} states for the platform-wide key: the secret signs and
 * verifies payments and must never reach a client once written.
 */
@Schema(name = "CompanyRazorpayConfigResponse")
public record CompanyRazorpayConfigResponse(
        boolean enabled,
        String keyId,
        @Schema(description = "Whether a key secret has ever been saved — never the secret itself.")
        boolean keySecretConfigured,
        Instant updatedAt
) {
}
