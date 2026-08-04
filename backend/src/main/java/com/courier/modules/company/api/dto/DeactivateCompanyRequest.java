package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/companies/{id}/deactivate}.
 *
 * <p>Unlike suspension the reason is optional: deactivating a dormant company is
 * routine housekeeping, not an accusation, and demanding a justification for it only
 * teaches operators to type "n/a". When given, it is written to the audit trail, the
 * event and the company's remarks.
 */
@Schema(name = "DeactivateCompanyRequest", description = "Why the company is being deactivated")
public record DeactivateCompanyRequest(

        @Size(max = 500)
        @Schema(example = "Migrated to the group account; no bookings since March")
        String reason
) {
}
