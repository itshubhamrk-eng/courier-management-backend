package com.courier.modules.subscription.api.dto;

import com.courier.modules.subscription.domain.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compact projection used for list responses.
 *
 * <p>A page of twenty plans does not need twenty full quota sets and feature-flag maps;
 * a list view renders the price and the tier. Clients fetch
 * {@code GET /subscription-plans/{id}} for the detail.
 */
@Schema(name = "SubscriptionPlanSummary", description = "Subscription plan, list projection")
public record SubscriptionPlanSummary(

        UUID id,
        String planCode,
        String planName,
        PlanType planType,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String currency,
        Integer trialDays,
        boolean isActive,
        Integer displayOrder,
        Long version
) {
}
