package com.courier.modules.finance.application;

import com.courier.modules.company.application.event.BranchEvent;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives every new branch its wallet.
 *
 * <p>"A wallet is created automatically when a branch is created" is a Finance rule, so it
 * lives in Finance. The company module publishes {@code BranchCreated} and knows nothing
 * about wallets — the alternative, calling a wallet service from {@code BranchServiceImpl},
 * would make branch creation fail when an unrelated module does.
 *
 * <p>AFTER_COMMIT and {@code REQUIRES_NEW}: the branch is already durable when this runs, so
 * a wallet failure cannot roll it back. The cost is a window where a branch exists without a
 * wallet, and it is covered — {@code WalletService.getOrCreateForBranch} is idempotent and
 * every read path goes through it, so a missed provisioning is repaired on first access.
 * That same fallback is what gives branches created before this module a wallet.
 *
 * <p>The listener runs on the committing thread, so {@code CompanyContext} and the security
 * context are still bound; the company is re-bound from the event anyway rather than assumed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletProvisioningListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BranchEvent.BranchCreated event) {
        try {
            CompanyContext.runAs(event.companyId(),
                    () -> walletService.getOrCreateForBranch(event.branchId()));
        } catch (RuntimeException e) {
            // Never fail the request that created the branch. The branch is committed; the
            // wallet will be created on first access.
            log.error("Could not provision a wallet for branch {} ({}); it will be created "
                    + "on first access", event.branchCode(), event.branchId(), e);
        }
    }
}
