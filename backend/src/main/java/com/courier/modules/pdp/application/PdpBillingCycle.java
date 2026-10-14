package com.courier.modules.pdp.application;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.customerbilling.application.BillingCycle;

import java.time.LocalDate;
import java.util.Optional;

/** Which period a partner's next scheduled statement covers. Pure date maths, no I/O. */
public final class PdpBillingCycle {

    private PdpBillingCycle() {
    }

    /** A statement period, both ends inclusive. */
    public record Window(LocalDate from, LocalDate to) {
    }

    /**
     * The period to bill now, or empty when nothing is due. The cycle end is the last fully elapsed
     * cycle before {@code today}. It is due once, until a statement reaches it ({@code lastBilledTo}
     * at or past it), and only when there are unbilled earnings ({@code earliestUnbilled} non-null).
     * The period starts the day after the previous statement, else at the oldest unbilled earning.
     */
    public static Optional<Window> due(BillingFrequency frequency, LocalDate today, LocalDate lastBilledTo,
                                       LocalDate earliestUnbilled) {
        LocalDate cycleEnd = BillingCycle.lastClosedCycleEnd(frequency, today);
        if (lastBilledTo != null && !lastBilledTo.isBefore(cycleEnd)) {
            return Optional.empty();
        }
        if (earliestUnbilled == null) {
            return Optional.empty();
        }
        LocalDate from = lastBilledTo != null && lastBilledTo.plusDays(1).isAfter(earliestUnbilled)
                ? lastBilledTo.plusDays(1) : earliestUnbilled;
        return from.isAfter(cycleEnd) ? Optional.empty() : Optional.of(new Window(from, cycleEnd));
    }
}
