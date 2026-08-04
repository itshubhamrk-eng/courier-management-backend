package com.courier.modules.company.api.dto;

import com.courier.modules.subscription.domain.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Extend a company's paid window.
 *
 * <p>The new window starts from the later of the current end date and today, so paying
 * early keeps the days already bought and paying late does not bill for the gap. The
 * request therefore has no start date — it is not the caller's to choose.
 */
@Schema(name = "RenewSubscriptionRequest",
        description = "Extend a company's subscription (SUPER_ADMIN)")
public record RenewSubscriptionRequest(

        @Schema(description = "Optional upgrade or downgrade taking effect with the new "
                + "period. The current plan continues when omitted.")
        UUID subscriptionPlanId,

        @Schema(description = "Length of one paid period. Required unless endDate is given.",
                example = "MONTHLY")
        BillingCycle billingCycle,

        @Min(value = 1, message = "A renewal covers at least one period")
        @Max(value = 120, message = "At most 120 periods may be paid for at once")
        @Schema(description = "How many billing cycles are being paid for.",
                defaultValue = "1")
        Integer periods,

        @Schema(description = "Explicit new end date, overriding billingCycle × periods. "
                + "Must be after the current end date.")
        LocalDate endDate,

        @Size(max = 500, message = "Remarks must be at most 500 characters")
        @Schema(description = "Free text kept with the company and the audit entry, "
                + "e.g. an invoice number.")
        String remarks) {

    public int periodsOrOne() {
        return periods == null || periods < 1 ? 1 : periods;
    }
}
