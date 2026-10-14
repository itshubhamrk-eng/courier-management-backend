package com.courier.modules.communication.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fires the morning business summary. The first tick of the day is 09:00 IST; later ticks (every
 * 10 minutes until 20:50) exist only so a send that failed, or a day the app was down at 09:00,
 * heals itself — {@code daily_report_log}'s unique key makes every tick after the first a no-op
 * for anyone already sent. Always reports the PREVIOUS calendar day in the configured zone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReportJob {

    private final DailyReportService service;

    @Value("${app.communication.daily-report.enabled:true}")
    private boolean enabled = true;

    @Value("${app.communication.daily-report.zone:Asia/Kolkata}")
    private String zone = "Asia/Kolkata";

    @Scheduled(cron = "${app.communication.daily-report.cron:0 0/10 9-20 * * *}",
            zone = "${app.communication.daily-report.zone:Asia/Kolkata}")
    public void run() {
        if (!enabled) {
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneId.of(zone)).minusDays(1);
        int attempted = service.sendReportsFor(yesterday);
        if (attempted > 0) {
            log.info("Daily business summary for {}: {} send(s) attempted", yesterday, attempted);
        }
    }
}
