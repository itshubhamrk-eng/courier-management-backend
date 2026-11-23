package com.courier.modules.communication.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BranchReportJobTest {

    @Test
    void januaryFirstReportsDecember() {
        assertThat(BranchReportJob.previousMonth(LocalDate.of(2027, 1, 1)))
                .containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    void marchFirstReportsFebruary() {
        assertThat(BranchReportJob.previousMonth(LocalDate.of(2026, 3, 1)))
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    @Test
    void marchFirstOfALeapYearEndsOnTheTwentyNinth() {
        assertThat(BranchReportJob.previousMonth(LocalDate.of(2028, 3, 1)))
                .containsExactly(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));
    }

    @Test
    void runPassesThePreviousMonthToTheService() {
        BranchReportService service = mock(BranchReportService.class);
        new BranchReportJob(service).run();
        LocalDate[] expected = BranchReportJob.previousMonth(
                LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        verify(service).sendReportsFor(expected[0], expected[1]);
    }

    @Test
    void disabledJobDoesNothing() {
        BranchReportService service = mock(BranchReportService.class);
        BranchReportJob job = new BranchReportJob(service);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "enabled", false);
        job.run();
        verifyNoInteractions(service);
    }
}
