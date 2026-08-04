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
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Body of {@code POST /api/v1/subscription-plans}.
 *
 * <p>Bean Validation covers shape only — required, in range, well formed. Rules that
 * depend on the tier (a TRIAL must be free, an ENTERPRISE plan has no quotas) live in
 * {@code SubscriptionPlan.applyTypeInvariants()}, because they must hold for every
 * write path, not only for one that happened to arrive over HTTP.
 *
 * <p><b>Omit a quota field, or send null, to mean unlimited.</b> Zero is not unlimited;
 * it is a plan nobody can use, so quotas are validated as at least 1.
 */
@Schema(name = "CreateSubscriptionPlanRequest", description = "New subscription plan")
public record CreateSubscriptionPlanRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,48}[A-Za-z0-9]$",
                message = "must be 3-50 characters of letters, digits, hyphen or underscore")
        @Schema(description = "Stable machine key, uppercased on save. Immutable afterwards.",
                example = "STANDARD_MONTHLY")
        String planCode,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "Standard")
        String planName,

        @Size(max = 500)
        String description,

        @NotNull
        @Schema(example = "STANDARD")
        PlanType planType,

        @NotNull
        @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "9999999999999.9999")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "4999.0000")
        BigDecimal monthlyPrice,

        @NotNull
        @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "9999999999999.9999")
        @Digits(integer = 15, fraction = 4)
        @Schema(example = "49990.0000")
        BigDecimal yearlyPrice,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO-4217 code")
        @Schema(description = "ISO-4217. Defaults to INR when omitted.", example = "INR")
        String currency,

        @Min(0)
        @Max(365)
        @Schema(description = "Free days granted on signup. Required to be at least 1 for a TRIAL plan.",
                example = "14")
        Integer trialDays,

        @Min(1) @Schema(description = "Null means unlimited", example = "25") Integer maxUsers,
        @Min(1) @Schema(description = "Null means unlimited", example = "5") Integer maxBranches,
        @Min(1) @Schema(description = "Null means unlimited", example = "3") Integer maxHubs,
        @Min(1) @Schema(description = "Null means unlimited", example = "10000") Integer maxCustomers,
        @Min(1) @Schema(description = "Null means unlimited", example = "50") Integer maxDrivers,
        @Min(1) @Schema(description = "Null means unlimited", example = "20") Integer maxVehicles,
        @Min(1) @Schema(description = "Null means unlimited", example = "500") Integer maxDailyBookings,
        @Min(1) @Schema(description = "Null means unlimited", example = "12000") Integer maxMonthlyBookings,
        @Min(1) @Schema(description = "Gigabytes. Null means unlimited", example = "50") Integer storageLimitGb,
        @Min(1) @Schema(description = "Requests per minute. Null means unlimited", example = "600") Integer apiRateLimit,

        @Schema(description = "Feature toggles",
                example = "{\"bulkBooking\": true, \"podImage\": false}")
        Map<String, Object> featureFlags,

        @Schema(description = "Defaults to true", example = "true")
        Boolean isActive,

        @Min(0)
        @Schema(description = "Ascending sort key for pricing pages", example = "20")
        Integer displayOrder
) {
}
