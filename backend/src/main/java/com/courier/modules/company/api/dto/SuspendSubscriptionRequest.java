package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/companies/{id}/subscription/suspend}.
 *
 * <p>Mandatory, for the same reason company suspension's is: this stops a paying
 * customer working, and the first thing anyone will ask is why.
 */
@Schema(name = "SuspendSubscriptionRequest",
        description = "Why the subscription is being suspended")
public record SuspendSubscriptionRequest(

        @NotBlank
        @Size(max = 500)
        @Schema(example = "Chargeback on invoice INV-2026-0042")
        String reason
) {
}
