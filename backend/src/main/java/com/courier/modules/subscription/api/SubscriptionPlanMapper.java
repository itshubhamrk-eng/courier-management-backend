package com.courier.modules.subscription.api;

import com.courier.modules.subscription.api.dto.CreateSubscriptionPlanRequest;
import com.courier.modules.subscription.api.dto.SubscriptionPlanResponse;
import com.courier.modules.subscription.api.dto.SubscriptionPlanSummary;
import com.courier.modules.subscription.api.dto.UpdateSubscriptionPlanRequest;
import com.courier.modules.subscription.application.command.CreateSubscriptionPlanCommand;
import com.courier.modules.subscription.application.command.UpdateSubscriptionPlanCommand;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import org.springframework.stereotype.Component;

/**
 * Translates between the wire contract and the application/domain types.
 *
 * <p>Hand-written rather than generated: the project has no MapStruct dependency, and
 * with a flat record on both sides a generated mapper would save nothing while hiding
 * where the null-means-unlimited convention is preserved.
 *
 * <p>Lives in {@code api} because the DTOs are the API's contract — the application and
 * domain layers must not know they exist ({@code MEMORY/ARCHITECTURE.md} §1).
 */
@Component
public class SubscriptionPlanMapper {

    public CreateSubscriptionPlanCommand toCommand(CreateSubscriptionPlanRequest request) {
        return new CreateSubscriptionPlanCommand(
                request.planCode(),
                request.planName(),
                request.description(),
                request.planType(),
                request.monthlyPrice(),
                request.yearlyPrice(),
                request.currency(),
                request.trialDays(),
                request.maxUsers(),
                request.maxBranches(),
                request.maxHubs(),
                request.maxCustomers(),
                request.maxDrivers(),
                request.maxVehicles(),
                request.maxDailyBookings(),
                request.maxMonthlyBookings(),
                request.storageLimitGb(),
                request.apiRateLimit(),
                request.featureFlags(),
                request.isActive(),
                request.displayOrder());
    }

    public UpdateSubscriptionPlanCommand toCommand(UpdateSubscriptionPlanRequest request) {
        return new UpdateSubscriptionPlanCommand(
                request.planName(),
                request.description(),
                request.planType(),
                request.monthlyPrice(),
                request.yearlyPrice(),
                request.currency(),
                request.trialDays(),
                request.maxUsers(),
                request.maxBranches(),
                request.maxHubs(),
                request.maxCustomers(),
                request.maxDrivers(),
                request.maxVehicles(),
                request.maxDailyBookings(),
                request.maxMonthlyBookings(),
                request.storageLimitGb(),
                request.apiRateLimit(),
                request.featureFlags(),
                request.displayOrder(),
                request.version());
    }

    public SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getDescription(),
                plan.getPlanType(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getCurrency(),
                plan.getTrialDays(),
                plan.getMaxUsers(),
                plan.getMaxBranches(),
                plan.getMaxHubs(),
                plan.getMaxCustomers(),
                plan.getMaxDrivers(),
                plan.getMaxVehicles(),
                plan.getMaxDailyBookings(),
                plan.getMaxMonthlyBookings(),
                plan.getStorageLimitGb(),
                plan.getApiRateLimit(),
                plan.getFeatureFlags(),
                plan.isActive(),
                plan.getDisplayOrder(),
                plan.isUnlimited(),
                plan.getCreatedBy(),
                plan.getCreatedAt(),
                plan.getUpdatedBy(),
                plan.getUpdatedAt(),
                plan.getVersion());
    }

    public SubscriptionPlanSummary toSummary(SubscriptionPlan plan) {
        return new SubscriptionPlanSummary(
                plan.getId(),
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getPlanType(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getCurrency(),
                plan.getTrialDays(),
                plan.isActive(),
                plan.getDisplayOrder(),
                plan.getVersion());
    }
}
