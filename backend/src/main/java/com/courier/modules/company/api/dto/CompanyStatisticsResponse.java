package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.CompanyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What a super admin needs to know about one company at a glance.
 *
 * <p><b>There is no {@code shipmentCount}.</b> {@code modules/shipment} does not exist,
 * and a field that is always zero reads as "this company has booked nothing" rather
 * than "nobody has built this yet" — the two are indistinguishable to whoever is
 * looking at the screen. The field arrives with the module that can populate it.
 *
 * <p>Counts include every row the company owns regardless of who created it; the
 * {@code active*} figures are the subset that is currently usable.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CompanyStatisticsResponse",
        description = "Counts and subscription position for one company (SUPER_ADMIN)")
public record CompanyStatisticsResponse(

        UUID id,
        UUID companyId,
        String companyCode,
        String companyName,
        CompanyStatus status,

        @Schema(description = "Plan the company is on right now")
        UUID subscriptionPlanId,
        String planCode,
        String planName,

        LocalDate trialEndDate,
        LocalDate subscriptionStartDate,
        LocalDate subscriptionEndDate,

        @Schema(description = "Days until the trial or subscription ends, whichever is "
                + "sooner. Negative once it has lapsed, null when neither is dated.")
        Long daysToExpiry,

        @Schema(description = "Users of this company, excluding soft-deleted rows")
        long userCount,

        @Schema(description = "Users whose status is ACTIVE")
        long activeUserCount,

        @Schema(description = "Users awaiting their first sign-in")
        long pendingUserCount,

        @Schema(description = "Branches of this company, excluding soft-deleted rows")
        long branchCount,

        @Schema(description = "Branches whose status is ACTIVE")
        long activeBranchCount,

        @Schema(description = "Roles defined for this company, seeded and custom")
        long roleCount,

        @Schema(description = "Plan ceiling on users; null means unlimited")
        Integer maxUsers,

        @Schema(description = "Plan ceiling on branches; null means unlimited")
        Integer maxBranches,

        @Schema(description = "True when one more user would exceed the plan's ceiling")
        boolean userQuotaReached,

        @Schema(description = "True when one more branch would exceed the plan's ceiling")
        boolean branchQuotaReached) {
}
