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

/**
 * Checks online recharges that never got an answer. A recharge stays {@code PENDING} when the
 * browser was closed after paying and the webhook never arrived (or is not configured):
 * every couple of minutes this asks the gateway, per company's own Razorpay account, what
 * became of each such order and settles, fails or leaves it. {@code WalletService
 * #reconcileRecharge} is idempotent on the gateway payment id, so racing the browser or the
 * webhook cannot credit twice.
 *
 * <p>Runs with no {@code CompanyContext} bound (a scheduler thread starts clean), so the
 * feed query is genuinely cross-company; each order is then handled inside {@code
 * CompanyContext.runAs} for its own company, the same shape as {@code TicketSlaSweepJob}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeReconciliationJob {

    /** Give the browser flow a head start before the scheduler steps in. */
    private static final Duration MIN_AGE = Duration.ofMinutes(2);
    private static final Duration RECHECK_AFTER = Duration.ofMinutes(1);
    private static final int BATCH = 50;

    private final RechargeOrderRepository repository;
    private final WalletService walletService;

    @Scheduled(fixedDelayString = "120000", initialDelayString = "90000")
    public void reconcile() {
        Instant now = Instant.now();
        List<RechargeOrder> pending = repository.findPendingToReconcile(
                now.minus(MIN_AGE), now.minus(RECHECK_AFTER), PageRequest.of(0, BATCH));
        if (pending.isEmpty()) {
            return;
        }
        int handled = 0;
        for (RechargeOrder order : pending) {
            try {
                CompanyContext.runAs(order.getCompanyId(), () -> walletService.reconcileRecharge(order.getId()));
                handled++;
            } catch (Exception e) {
                log.error("Recharge reconciliation failed for {} ({})", order.getTransactionNo(), order.getId(), e);
            }
        }
        log.info("Recharge reconciliation: {} pending order(s) checked", handled);
    }
}
