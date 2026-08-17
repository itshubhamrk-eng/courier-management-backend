package com.courier.modules.support.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly entry point for {@link ShipmentSlaSweepService}. Kept a thin wrapper so the
 * actual sweep logic is unit-testable without Spring's scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentSlaSweepJob {

    private final ShipmentSlaSweepService sweepService;

    /** Top of every hour. */
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        log.debug("ShipmentSlaSweepJob firing");
        sweepService.sweepAllCompanies();
    }
}
