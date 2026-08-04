package com.courier.modules.company.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by User Management.
 *
 * <p>Sealed, so a new event type cannot be silently dropped by an existing {@code switch}.
 * Published through {@code ApplicationEventPublisher} and consumed
 * {@code @TransactionalEventListener(AFTER_COMMIT)} — nothing reacts to a user whose
 * transaction rolled back. In-process, like {@code CompanyEvent}; an outbox with no
 * consumer would be infrastructure for its own sake.
 *
 * <p>Events carry identifiers and minimal context, never the entity, which would be stale
 * by the time a listener reads it.
 */
public sealed interface UserEvent {

    UUID userId();

    UUID companyId();

    Instant occurredAt();

    record UserCreated(UUID userId, UUID companyId, String email, UUID adminUserId,
                       boolean verificationEmailSent, Instant occurredAt) implements UserEvent {
    }

    record UserUpdated(UUID userId, UUID companyId, java.util.Set<String> changedFields,
                       Instant occurredAt) implements UserEvent {
    }

    record UserActivated(UUID userId, UUID companyId, Instant occurredAt) implements UserEvent {
    }

    record UserDeactivated(UUID userId, UUID companyId, Instant occurredAt) implements UserEvent {
    }

    record UserLocked(UUID userId, UUID companyId, String reason, Instant occurredAt)
            implements UserEvent {
    }

    record UserUnlocked(UUID userId, UUID companyId, Instant occurredAt) implements UserEvent {
    }

    record PasswordReset(UUID userId, UUID companyId, boolean mustChange, Instant occurredAt)
            implements UserEvent {
    }

    record RoleAssigned(UUID userId, UUID companyId, String roleCode, Instant occurredAt)
            implements UserEvent {
    }

    record RoleRemoved(UUID userId, UUID companyId, String roleCode, Instant occurredAt)
            implements UserEvent {
    }

    record BranchAssigned(UUID userId, UUID companyId, UUID branchId, Instant occurredAt)
            implements UserEvent {
    }

    record HubAssigned(UUID userId, UUID companyId, UUID hubId, Instant occurredAt)
            implements UserEvent {
    }
}
