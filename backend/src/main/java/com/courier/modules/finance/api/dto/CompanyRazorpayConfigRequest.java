package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.RazorpayMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /company-razorpay-config}. Test and live credentials are separate,
 * independently-saved pairs — both can be sent in the same request; {@code mode} only
 * decides which one wallet recharge actually uses.
 *
 * <p>Both {@code testKeySecret} and {@code liveKeySecret} are optional: blank or omitted
 * means "keep the one already stored" for that pair. There is no way to read either back
 * to confirm, by design — see {@link CompanyRazorpayConfigResponse}.
 */
@Schema(name = "CompanyRazorpayConfigRequest")
public record CompanyRazorpayConfigRequest(

        @Schema(description = "Use this company's own Razorpay account instead of the platform-wide gateway.")
        boolean enabled,

        @NotNull
        @Schema(description = "Which saved credential pair is actually used for wallet recharge.")
        RazorpayMode mode,

        @Size(max = 255)
        @Schema(description = "The test publishable key id.")
        String testKeyId,

        @Size(max = 500)
        @Schema(description = "The test signing secret. Blank keeps the one already stored.")
        String testKeySecret,

        @Size(max = 255)
        @Schema(description = "The live publishable key id.")
        String liveKeyId,

        @Size(max = 500)
        @Schema(description = "The live signing secret. Blank keeps the one already stored.")
        String liveKeySecret
) {
}
