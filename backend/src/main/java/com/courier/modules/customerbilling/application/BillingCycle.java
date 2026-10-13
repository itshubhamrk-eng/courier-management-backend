package com.courier.modules.customerbilling.application;

import com.courier.modules.customer.domain.BillingFrequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * When a billing cycle closes. {@link #lastClosedCycleEnd} is the end date of the most recent
 * cycle that has fully elapsed before {@code today}; the cycle job bills everything up to it.
 * <ul>
 *   <li>WEEKLY — weeks run Monday–Sunday; the last Sunday before today.</li>
 *   <li>FIFTEEN_DAYS — 1st–15th and 16th–month end; the 15th, or the last day of the previous month.</li>
 *   <li>MONTHLY — the last day of the previous month.</li>
 * </ul>
 */
public final class BillingCycle {

    private BillingCycle() {
    }

    public static LocalDate lastClosedCycleEnd(BillingFrequency frequency, LocalDate today) {
        return switch (frequency) {
            case WEEKLY -> today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            case FIFTEEN_DAYS -> today.getDayOfMonth() > 15
                    ? today.withDayOfMonth(15)
                    : today.withDayOfMonth(1).minusDays(1);
            case MONTHLY -> today.withDayOfMonth(1).minusDays(1);
        };
    }
}
