package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact projection for list responses.
 *
 * <p>A page of companies renders identity, status and who to call — not tax numbers,
 * addresses and branding. Clients fetch {@code GET /companies/{id}} for the detail,
 * which also keeps the more sensitive fields out of bulk exports.
 */
@Schema(name = "CompanySummaryResponse", description = "Company, list projection")
public record CompanySummaryResponse(

        UUID id,
        UUID companyId,
        String companyCode,
        String companyName,
        String displayName,
        CompanyStatus status,
        boolean isActive,
        UUID subscriptionPlanId,
        String email,
        String mobile,
        String city,
        String state,
        LocalDate trialEndDate,
        LocalDate subscriptionEndDate,
        Instant createdDate,
        Long version
) {
}
