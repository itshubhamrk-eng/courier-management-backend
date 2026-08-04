package com.courier.modules.company.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to user lifecycle events after the transaction commits.
 *
 * <p>Today the only reaction is an operational log line — the seam where token
 * revocation (lock/deactivate should end active sessions), welcome emails and the
 * eventual outbox relay will attach. Those belong to modules or infrastructure that do
 * not exist yet, so nothing is stubbed for them. {@code AFTER_COMMIT} keeps a listener
 * from ever acting on a user whose transaction rolled back.
 *
 * <p>Exhaustive by construction: {@link UserEvent} is sealed, so a new event type fails
 * to compile here rather than being missed.
 */
@Slf4j
@Component
public class UserEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserEvent event) {
        String detail = switch (event) {
            case UserEvent.UserCreated e -> "created (%s), admin email %s"
                    .formatted(e.email(), e.verificationEmailSent() ? "sent" : "NOT sent");
            case UserEvent.UserUpdated e -> "updated " + e.changedFields();
            case UserEvent.UserActivated e -> "activated";
            case UserEvent.UserDeactivated e -> "deactivated";
            case UserEvent.UserLocked e -> "locked: " + e.reason();
            case UserEvent.UserUnlocked e -> "unlocked";
            case UserEvent.PasswordReset e -> "password reset (mustChange=" + e.mustChange() + ")";
            case UserEvent.RoleAssigned e -> "role " + e.roleCode() + " assigned";
            case UserEvent.RoleRemoved e -> "role " + e.roleCode() + " removed";
            case UserEvent.BranchAssigned e -> "branch " + e.branchId() + " assigned";
            case UserEvent.HubAssigned e -> "hub " + e.hubId() + " assigned";
        };
        log.info("User {} [{}] {}", event.userId(), event.companyId(), detail);
    }
}
