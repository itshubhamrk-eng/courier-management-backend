package com.courier.modules.company.api.dto;

import com.courier.modules.subscription.domain.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Put a company on a plan for a defined, paid period.
 *
 * <p>Either supply a {@code billingCycle} — the normal case, where the end date is
 * derived — or an explicit {@code endDate} for a negotiated term that does not land on
 * a cycle boundary. Supplying both is allowed and the explicit date wins, because the
 * contract is the source of truth, not the calendar arithmetic.
 */
@Schema(name = "AssignSubscriptionRequest",
        description = "Move a company onto a subscription plan (SUPER_ADMIN)")
public record AssignSubscriptionRequest(

        @NotNull(message = "A subscription plan is required")
        @Schema(description = "Plan to move to. Must exist and be active.")
        UUID subscriptionPlanId,

        @Schema(description = "Length of one paid period. Required unless endDate is given.",
                example = "YEARLY")
        BillingCycle billingCycle,

        @Min(value = 1, message = "A subscription runs for at least one period")
        @Max(value = 120, message = "At most 120 periods may be paid for at once")
        @Schema(description = "How many billing cycles are being paid for.",
                defaultValue = "1")
        Integer periods,

        @Schema(description = "When the paid window opens. Today if omitted.")
        LocalDate startDate,

        @Schema(description = "Explicit end date, overriding billingCycle × periods.")
        LocalDate endDate,

        @Size(max = 500, message = "Remarks must be at most 500 characters")
        @Schema(description = "Free text kept with the company and the audit entry, "
                + "e.g. a purchase-order number.")
        String remarks) {

    /** One period unless the caller says otherwise — the overwhelmingly common case. */
    public int periodsOrOne() {
        return periods == null || periods < 1 ? 1 : periods;
    }
}
