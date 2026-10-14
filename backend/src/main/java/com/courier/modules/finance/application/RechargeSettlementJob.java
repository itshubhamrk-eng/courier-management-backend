package com.courier.modules.finance.application;

import com.courier.modules.finance.domain.RechargeOrder;
import com.courier.modules.finance.domain.RechargeOrderRepository;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every few hours, asks each company's Razorpay account which credited recharges have been
 * paid out to the bank. Razorpay settles daily, so a short interval would only repeat the
 * same answer. Same shape as {@link RechargeReconciliationJob}: the feed is cross-company, the
 * per-company work runs inside {@code CompanyContext.runAs}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeSettlementJob {

    /** A payment captured moments ago cannot be in a settlement yet. */
    private static final Duration MIN_AGE = Duration.ofHours(6);
    private static final int BATCH = 500;

    private final RechargeOrderRepository repository;
    private final RechargeSettlementSync sync;

    @Scheduled(fixedDelayString = "10800000", initialDelayString = "300000")
    public void run() {
        Instant now = Instant.now();
        List<RechargeOrder> awaiting = repository.findAwaitingBankSettlement(
                now.minus(Duration.ofDays(RechargeSettlementSync.MAX_LOOKBACK_DAYS)), now.minus(MIN_AGE),
                PageRequest.of(0, BATCH));
        if (awaiting.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> byCompany = awaiting.stream().collect(Collectors.groupingBy(
                RechargeOrder::getCompanyId, Collectors.mapping(RechargeOrder::getId, Collectors.toList())));
        int settled = 0;
        for (Map.Entry<UUID, List<UUID>> e : byCompany.entrySet()) {
            try {
                settled += CompanyContext.runAs(e.getKey(),
                        (java.util.function.Supplier<Integer>) () -> sync.sync(e.getKey(), e.getValue()));
            } catch (Exception ex) {
                log.error("Settlement sync failed for company {}", e.getKey(), ex);
            }
        }
        log.info("Recharge settlement sync: {} recharge(s) newly settled across {} company(ies)",
                settled, byCompany.size());
    }
}
