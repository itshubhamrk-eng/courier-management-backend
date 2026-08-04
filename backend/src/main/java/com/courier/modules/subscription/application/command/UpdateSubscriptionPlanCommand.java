package com.courier.modules.subscription.application.command;

import com.courier.modules.subscription.domain.PlanType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input to {@code SubscriptionPlanService.update}. A full replacement (PUT), not a patch.
 *
 * <p>{@code planCode} is absent on purpose: it is the stable key that companies and
 * invoices reference, so it is immutable after creation. Retiring a code means
 * deactivating that plan and creating a new one.
 *
 * @param expectedVersion the {@code version} the client last read. The update fails
 *                        with {@code 409 CONCURRENT_MODIFICATION} if the row has moved
 *                        on since — see {@code MEMORY/ARCHITECTURE.md} §4.
 */
public record UpdateSubscriptionPlanCommand(
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
        Integer displayOrder,
        Long expectedVersion
) {
}
