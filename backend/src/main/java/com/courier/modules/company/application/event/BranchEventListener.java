package com.courier.modules.company.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to branch lifecycle events after commit. Today it logs; the seam where
 * serviceability-cache eviction and downstream notifications will attach when the
 * shipment/network modules land. Exhaustive by construction — {@link BranchEvent} is
 * sealed.
 */
@Slf4j
@Component
public class BranchEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BranchEvent event) {
        String detail = switch (event) {
            case BranchEvent.BranchCreated e -> "created (%s, %s)".formatted(e.branchCode(), e.branchType());
            case BranchEvent.BranchUpdated e -> "updated " + e.changedFields();
            case BranchEvent.BranchActivated e -> "activated";
            case BranchEvent.BranchDeactivated e -> "deactivated";
            case BranchEvent.ManagerAssigned e -> "manager -> " + e.managerId();
        };
        log.info("Branch {} [{}] {}", event.branchId(), event.companyId(), detail);
    }
}
