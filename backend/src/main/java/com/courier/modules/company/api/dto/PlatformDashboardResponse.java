package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.CompanyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The super admin's home screen: the platform as a whole, not any one company.
 *
 * <p>Like {@link CompanyStatisticsResponse} it carries no shipment figure, for the same
 * reason — the module that would produce one does not exist, and a constant zero is a
 * lie told in a number.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PlatformDashboardResponse",
        description = "Platform-wide totals for the super admin console")
public record PlatformDashboardResponse(

        @Schema(description = "Companies on the platform, excluding soft-deleted rows")
        long companyCount,

        @Schema(description = "Companies per lifecycle state. Every status appears, "
                + "including the ones at zero, so a client can render a fixed set of "
                + "tiles without inventing the missing keys.")
        Map<CompanyStatus, Long> companiesByStatus,

        @Schema(description = "Companies whose trial or subscription has already lapsed")
        long expiredCount,

        @Schema(description = "Companies whose trial or subscription ends within 30 days, "
                + "including those already lapsed — the renewals worklist")
        long expiringSoonCount,

        @Schema(description = "Subscription plans in the catalogue")
        long planCount,

        @Schema(description = "Plans that may currently be assigned")
        long activePlanCount,

        @Schema(description = "The renewals worklist itself, soonest first, capped")
        List<Renewal> upcomingRenewals) {

    /**
     * One row of the renewals worklist.
     *
     * @param endDate the sooner of the trial and subscription end dates — which is the
     *                one that actually stops the company working
     */
    @Schema(name = "PlatformDashboardRenewal")
    public record Renewal(UUID id,
                          UUID companyId,
                          String companyCode,
                          String companyName,
                          CompanyStatus status,
                          LocalDate endDate,
                          long daysRemaining) {
    }
}
