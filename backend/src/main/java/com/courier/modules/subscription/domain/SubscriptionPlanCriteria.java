package com.courier.modules.subscription.domain;

import java.math.BigDecimal;

/**
 * Filter criteria for a catalogue search. Every field is optional; a null field means
 * "do not constrain on this".
 *
 * <p>Lives in {@code domain} rather than {@code api} so that both the controller (which
 * builds it from query parameters) and the service (which passes it to
 * {@link SubscriptionPlanSpecifications}) can use it without either depending on the
 * other's package.
 *
 * @param planType   exact tier match
 * @param active     true for the offered catalogue, false for retired plans
 * @param currency   ISO-4217, case-insensitive
 * @param minPrice   inclusive lower bound on monthly price
 * @param maxPrice   inclusive upper bound on monthly price
 * @param search     free text matched against code, name and description
 */
public record SubscriptionPlanCriteria(
        PlanType planType,
        Boolean active,
        String currency,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String search
) {

    public static SubscriptionPlanCriteria none() {
        return new SubscriptionPlanCriteria(null, null, null, null, null, null);
    }
}
