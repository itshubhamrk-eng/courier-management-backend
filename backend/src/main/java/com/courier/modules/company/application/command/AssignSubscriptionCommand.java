package com.courier.modules.company.application.command;

import com.courier.modules.subscription.domain.BillingCycle;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Put a company on a plan for a defined period.
 *
 * <p>Distinct from {@code UpdateCompanyCommand}, which also carries a plan id: that one
 * is a full-replacement edit of the company record and says nothing about billing
 * dates. This one is the commercial act — it moves the company onto the plan, opens a
 * paid window and activates it. Two different things that happened to share a field are
 * now two endpoints, each audited under its own action.
 *
 * @param subscriptionPlanId the plan to move to; must exist and be active
 * @param billingCycle       length of one paid period
 * @param periods            how many cycles are being paid for, at least one
 * @param startDate          when the window opens; today if omitted
 * @param endDate            explicit end, overriding {@code billingCycle × periods} —
 *                           for a negotiated term that does not fit a standard cycle
 * @param remarks            free text for the audit trail, e.g. a purchase-order number
 */
public record AssignSubscriptionCommand(UUID subscriptionPlanId,
                                        BillingCycle billingCycle,
                                        int periods,
                                        LocalDate startDate,
                                        LocalDate endDate,
                                        String remarks) {
}
