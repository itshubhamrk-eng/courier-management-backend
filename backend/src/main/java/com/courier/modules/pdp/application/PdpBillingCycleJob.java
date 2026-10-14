package com.courier.modules.pdp.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily 02:00 (server time; override with {@code pdp.billing.cron}): drafts each partner's statement once their cycle has closed. */
@Component
@RequiredArgsConstructor
public class PdpBillingCycleJob {

    private final PdpBillingCycleService cycleService;

    @Scheduled(cron = "${pdp.billing.cron:0 0 2 * * *}")
    public void run() {
        cycleService.runAll(PdpBillingCycleService.today());
    }
}
