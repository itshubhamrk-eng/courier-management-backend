package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /company-razorpay-config}.
 *
 * <p>{@code keySecret} is optional: blank or omitted means "keep the secret already
 * stored". There is no way to read it back to confirm, by design — see
 * {@link CompanyRazorpayConfigResponse}.
 */
@Schema(name = "CompanyRazorpayConfigRequest")
public record CompanyRazorpayConfigRequest(

        @Schema(description = "Use this company's own Razorpay account instead of the platform-wide gateway.")
        boolean enabled,

        @Size(max = 255)
        @Schema(description = "The publishable key id.")
        String keyId,

        @Size(max = 500)
        @Schema(description = "The signing secret. Blank keeps the one already stored.")
        String keySecret
) {
}
