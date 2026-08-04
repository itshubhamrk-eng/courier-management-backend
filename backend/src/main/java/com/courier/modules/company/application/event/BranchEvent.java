package com.courier.modules.company.application.event;

import com.courier.modules.company.domain.BranchType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by Branch Management. Sealed, consumed
 * {@code @TransactionalEventListener(AFTER_COMMIT)} — nothing reacts to a branch whose
 * transaction rolled back. In-process, like the other company events.
 */
public sealed interface BranchEvent {

    UUID branchId();

    UUID companyId();

    Instant occurredAt();

    record BranchCreated(UUID branchId, UUID companyId, String branchCode, BranchType branchType,
                         Instant occurredAt) implements BranchEvent {
    }

    record BranchUpdated(UUID branchId, UUID companyId, java.util.Set<String> changedFields,
                         Instant occurredAt) implements BranchEvent {
    }

    record BranchActivated(UUID branchId, UUID companyId, Instant occurredAt) implements BranchEvent {
    }

    record BranchDeactivated(UUID branchId, UUID companyId, Instant occurredAt) implements BranchEvent {
    }

    record ManagerAssigned(UUID branchId, UUID companyId, UUID managerId, UUID previousManagerId,
                           Instant occurredAt) implements BranchEvent {
    }
}
