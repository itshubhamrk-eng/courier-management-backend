package com.courier.modules.company.application.command;

import com.courier.modules.subscription.domain.BillingCycle;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Extend a company's paid window.
 *
 * <p>Renewal extends from the later of the current end date and today, so a customer
 * who pays a week early keeps the week they already bought, and one who pays a month
 * late does not get charged for the month they could not use. That single rule is the
 * whole reason this is not "set subscriptionEndDate".
 *
 * @param subscriptionPlanId optional upgrade or downgrade taking effect with the new
 *                           period; the current plan continues when omitted
 * @param billingCycle       length of one paid period
 * @param periods            how many cycles are being paid for, at least one
 * @param endDate            explicit end, overriding {@code billingCycle × periods};
 *                           must be after the current end date
 * @param remarks            free text for the audit trail, e.g. an invoice number
 */
public record RenewSubscriptionCommand(UUID subscriptionPlanId,
                                       BillingCycle billingCycle,
                                       int periods,
                                       LocalDate endDate,
                                       String remarks) {
}
