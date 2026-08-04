package com.courier.modules.subscription.api.dto;

import com.courier.modules.subscription.domain.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Body of {@code PUT /api/v1/subscription-plans/{id}}.
 *
 * <p>A full replacement, not a patch: an omitted field is written as null, which for a
 * quota means "unlimited". Send the whole object back, not a delta.
 *
 * <p>{@code planCode} cannot be changed and is not accepted here — companies and invoices
 * reference it. Activation is not accepted either; it has its own endpoints so that
 * turning a plan off is an explicit, separately audited act rather than a side effect
 * of an edit.
 */
@Schema(name = "UpdateSubscriptionPlanRequest", description = "Full replacement of a subscription plan")
public record UpdateSubscriptionPlanRequest(

        @NotBlank
        @Size(max = 100)
        String planName,

        @Size(max = 500)
        String description,

        @NotNull
        PlanType planType,

        @NotNull
        @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "9999999999999.9999")
        @Digits(integer = 15, fraction = 4)
        BigDecimal monthlyPrice,

        @NotNull
        @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "9999999999999.9999")
        @Digits(integer = 15, fraction = 4)
        BigDecimal yearlyPrice,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO-4217 code")
        String currency,

        @Min(0)
        @Max(365)
        Integer trialDays,

        @Min(1) @Schema(description = "Null means unlimited") Integer maxUsers,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxBranches,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxHubs,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxCustomers,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxDrivers,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxVehicles,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxDailyBookings,
        @Min(1) @Schema(description = "Null means unlimited") Integer maxMonthlyBookings,
        @Min(1) @Schema(description = "Gigabytes. Null means unlimited") Integer storageLimitGb,
        @Min(1) @Schema(description = "Requests per minute. Null means unlimited") Integer apiRateLimit,

        Map<String, Object> featureFlags,

        @Min(0)
        Integer displayOrder,

        /*
         * Mandatory. This is the whole point of optimistic locking: without the version
         * the client last read, two admins editing the same plan would both succeed and
         * the second would silently discard the first one's pricing change.
         */
        @NotNull
        @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.",
                example = "3")
        Long version
) {
}
