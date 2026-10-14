package com.courier.modules.pdp.application;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.pdp.application.PdpBillingCycle.Window;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PdpBillingCycleTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void monthlyBillsPreviousMonthFromOldestUnbilled() {
        assertThat(PdpBillingCycle.due(BillingFrequency.MONTHLY, d("2026-10-14"), null, d("2026-09-03")))
                .contains(new Window(d("2026-09-03"), d("2026-09-30")));
    }

    @Test
    void fifteenDayCycleClosesOnThe15th() {
        assertThat(PdpBillingCycle.due(BillingFrequency.FIFTEEN_DAYS, d("2026-10-20"), null, d("2026-10-02")))
                .contains(new Window(d("2026-10-02"), d("2026-10-15")));
    }

    @Test
    void weeklyClosesOnLastSunday() {
        // 2026-10-14 is a Wednesday; last Sunday is 2026-10-11.
        assertThat(PdpBillingCycle.due(BillingFrequency.WEEKLY, d("2026-10-14"), d("2026-10-04"), d("2026-10-06")))
                .contains(new Window(d("2026-10-06"), d("2026-10-11")));
    }

    @Test
    void startsDayAfterPreviousStatement() {
        assertThat(PdpBillingCycle.due(BillingFrequency.MONTHLY, d("2026-11-02"), d("2026-09-30"), d("2026-09-10")))
                .contains(new Window(d("2026-10-01"), d("2026-10-31")));
    }

    @Test
    void nothingDueOnceStatementReachedCycleEnd() {
        assertThat(PdpBillingCycle.due(BillingFrequency.MONTHLY, d("2026-10-14"), d("2026-09-30"), d("2026-10-01")))
                .isEmpty();
    }

    @Test
    void nothingDueWithoutUnbilledEarnings() {
        assertThat(PdpBillingCycle.due(BillingFrequency.MONTHLY, d("2026-10-14"), null, null)).isEqualTo(Optional.empty());
    }

    @Test
    void nothingDueWhenOldestEarningIsInTheOpenCycle() {
        assertThat(PdpBillingCycle.due(BillingFrequency.MONTHLY, d("2026-10-14"), null, d("2026-10-05"))).isEmpty();
    }
}
