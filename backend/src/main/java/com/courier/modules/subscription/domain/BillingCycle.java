package com.courier.modules.subscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How long one paid period of a subscription lasts.
 *
 * <p>A plan carries both a monthly and a yearly price, so the cycle is not a property
 * of the plan — it is chosen per company when the subscription is assigned or renewed.
 * Keeping it here rather than as a bare {@code int months} means the price and the end
 * date are derived from one decision instead of two that can disagree.
 */
public enum BillingCycle {

    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }

    /**
     * The end of {@code periods} cycles starting on {@code start}.
     *
     * <p>Exclusive of nothing and inclusive of nothing in particular — it is the date
     * the next payment is due, which is what every invoice and every renewal reminder
     * quotes. {@code plusMonths} clamps 31 January + 1 month to 28/29 February, which
     * is the behaviour a customer expects and the one every billing system uses.
     */
    public LocalDate endOf(LocalDate start, int periods) {
        if (start == null) {
            throw new IllegalArgumentException("A subscription period needs a start date");
        }
        if (periods < 1) {
            throw new IllegalArgumentException("A subscription runs for at least one period");
        }
        return start.plusMonths((long) months * periods);
    }

    /** The list price of one period on the given plan, before any negotiated discount. */
    public BigDecimal priceOn(SubscriptionPlan plan) {
        return this == YEARLY
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice().multiply(BigDecimal.valueOf(months));
    }
}
