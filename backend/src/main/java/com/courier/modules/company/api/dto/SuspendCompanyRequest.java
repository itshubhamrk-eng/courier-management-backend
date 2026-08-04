package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/companies/{id}/suspend}.
 *
 * <p>The reason is mandatory because suspension cuts a paying customer off from the
 * platform, and "why is Acme suspended?" is the first question support will ask. It is
 * written to the audit trail, the suspension event and the company's remarks.
 */
@Schema(name = "SuspendCompanyRequest", description = "Why the company is being suspended")
public record SuspendCompanyRequest(

        @NotBlank
        @Size(max = 500)
        @Schema(example = "Non-payment of invoice INV-2026-0042, 45 days overdue")
        String reason
) {
}
