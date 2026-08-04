package com.courier.modules.subscription.api.dto;

import com.courier.modules.subscription.domain.PlanType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full representation of a subscription plan.
 *
 * <p>{@code null} on a quota field means <b>unlimited</b>, and those nulls are
 * serialised rather than dropped: a client cannot distinguish "unlimited" from "field
 * missing" if the key disappears, and the global Jackson setting omits nulls. Hence the
 * explicit {@code @JsonInclude(ALWAYS)} on this record.
 *
 * <p>The timestamps are the project's {@code createdAt}/{@code updatedAt} audit columns
 * from {@code BaseEntity}; {@code version} is what a client must echo back in a
 * {@code PUT}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "SubscriptionPlanResponse", description = "Subscription plan in full")
public record SubscriptionPlanResponse(

        UUID id,
        String planCode,
        String planName,
        String description,
        PlanType planType,

        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String currency,
        Integer trialDays,

        @Schema(description = "Null means unlimited") Integer maxUsers,
        @Schema(description = "Null means unlimited") Integer maxBranches,
        @Schema(description = "Null means unlimited") Integer maxHubs,
        @Schema(description = "Null means unlimited") Integer maxCustomers,
        @Schema(description = "Null means unlimited") Integer maxDrivers,
        @Schema(description = "Null means unlimited") Integer maxVehicles,
        @Schema(description = "Null means unlimited") Integer maxDailyBookings,
        @Schema(description = "Null means unlimited") Integer maxMonthlyBookings,
        @Schema(description = "Gigabytes. Null means unlimited") Integer storageLimitGb,
        @Schema(description = "Requests per minute. Null means unlimited") Integer apiRateLimit,

        Map<String, Object> featureFlags,

        @Schema(description = "Whether the plan may be assigned to new companies")
        boolean isActive,

        Integer displayOrder,

        @Schema(description = "True when every quota on this plan is uncapped")
        boolean unlimited,

        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,

        @Schema(description = "Echo this back in a PUT to detect concurrent edits")
        Long version
) {
}
