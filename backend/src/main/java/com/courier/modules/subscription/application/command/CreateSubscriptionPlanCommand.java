package com.courier.modules.subscription.application.command;

import com.courier.modules.subscription.domain.PlanType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input to {@code SubscriptionPlanService.create}.
 *
 * <p>The service takes this rather than the REST request DTO so that {@code application}
 * does not depend on {@code api} — the dependency rule is
 * {@code api -> application -> domain} ({@code MEMORY/ARCHITECTURE.md} §1). The mapper
 * in the {@code api} layer converts between the two.
 *
 * <p>Every quota field is nullable and null means <b>unlimited</b>.
 */
public record CreateSubscriptionPlanCommand(
        String planCode,
        String planName,
        String description,
        PlanType planType,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String currency,
        Integer trialDays,
        Integer maxUsers,
        Integer maxBranches,
        Integer maxHubs,
        Integer maxCustomers,
        Integer maxDrivers,
        Integer maxVehicles,
        Integer maxDailyBookings,
        Integer maxMonthlyBookings,
        Integer storageLimitGb,
        Integer apiRateLimit,
        Map<String, Object> featureFlags,
        Boolean active,
        Integer displayOrder
) {
}
