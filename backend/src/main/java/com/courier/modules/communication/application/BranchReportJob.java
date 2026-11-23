package com.courier.modules.communication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Sends last month's branch statements on the 1st, 09:00-20:50 IST every 10 minutes. Only the
 * first tick does work for a branch: {@code branch_report_log}'s unique key makes later ticks a
 * no-op for anyone already sent, while a failed send is retried up to the attempt cap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BranchReportJob {

    private final BranchReportService service;

    @Value("${app.communication.branch-report.enabled:true}")
    private boolean enabled = true;

    @Value("${app.communication.branch-report.zone:Asia/Kolkata}")
    private String zone = "Asia/Kolkata";

    @Scheduled(cron = "${app.communication.branch-report.cron:0 0/10 9-20 1 * *}",
            zone = "${app.communication.branch-report.zone:Asia/Kolkata}")
    public void run() {
        if (!enabled) {
            return;
        }
        LocalDate[] period = previousMonth(LocalDate.now(ZoneId.of(zone)));
        int attempted = service.sendReportsFor(period[0], period[1]);
        if (attempted > 0) {
            log.info("Branch monthly report for {} to {}: {} send(s) attempted", period[0], period[1], attempted);
        }
    }

    /** {first day, last day} (both inclusive) of the calendar month before {@code today}'s. */
    static LocalDate[] previousMonth(LocalDate today) {
        YearMonth previous = YearMonth.from(today).minusMonths(1);
        return new LocalDate[]{previous.atDay(1), previous.atEndOfMonth()};
    }
}
