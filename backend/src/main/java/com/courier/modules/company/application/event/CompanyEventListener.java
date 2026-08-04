package com.courier.modules.company.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to company lifecycle events after the transaction commits.
 *
 * <p>{@code AFTER_COMMIT} is the point: a listener must never act on a company whose
 * transaction later rolls back — the alternative is emails about companies that do not
 * exist.
 *
 * <p>Today the only reaction is an operational log line at INFO, which is what makes
 * "when did this company get suspended, and by which release" answerable without a
 * database query. This class is the seam where cache eviction, billing notification and
 * the eventual outbox relay will attach; those belong to modules that do not exist yet,
 * so nothing is stubbed for them here.
 */
@Slf4j
@Component
public class CompanyEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CompanyEvent event) {
        // Exhaustive by construction: CompanyEvent is sealed, so a new event type
        // fails to compile here rather than being silently dropped.
        String detail = switch (event) {
            case CompanyEvent.CompanyCreated created -> "created on plan %s with admin %s (%s)"
                    .formatted(created.subscriptionPlanId(), created.adminUserId(), created.status());
            case CompanyEvent.CompanyUpdated updated -> "updated fields %s"
                    .formatted(updated.changedFields().keySet());
            case CompanyEvent.CompanyActivated activated -> "activated from %s"
                    .formatted(activated.previousStatus());
            case CompanyEvent.CompanySuspended suspended -> "suspended from %s: %s"
                    .formatted(suspended.previousStatus(), suspended.reason());
            case CompanyEvent.CompanyDeactivated deactivated -> "deactivated from %s: %s"
                    .formatted(deactivated.previousStatus(), deactivated.reason());
            case CompanyEvent.CompanyExpired expired -> "expired from %s"
                    .formatted(expired.previousStatus());
            case CompanyEvent.SubscriptionChanged subscription -> "subscription %s to plan %s (%s → %s)"
                    .formatted(subscription.action(), subscription.planCode(),
                            subscription.subscriptionStartDate(), subscription.subscriptionEndDate());
        };

        log.info("Company {} [{}] {}", event.companyCode(), event.companyId(), detail);
    }
}
