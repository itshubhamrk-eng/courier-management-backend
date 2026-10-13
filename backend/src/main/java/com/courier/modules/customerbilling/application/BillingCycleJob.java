package com.courier.modules.customerbilling.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Daily 01:30 (server time; override with {@code billing.cycle.cron}): issues each business customer's invoice when their weekly / 15-day / monthly cycle has closed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCycleJob {

    private final BillingCycleService cycleService;

    @Scheduled(cron = "${billing.cycle.cron:0 30 1 * * *}")
    public void run() {
        cycleService.runAll(LocalDate.now(ZoneOffset.UTC));
    }
}
