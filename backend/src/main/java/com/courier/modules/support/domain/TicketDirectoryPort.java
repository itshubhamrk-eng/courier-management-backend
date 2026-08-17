package com.courier.modules.support.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Ticket Support needs to know about users and branches — and nothing more. Same
 * seam {@code finance.domain.BranchDirectoryPort} draws: this module owns the interface,
 * {@code modules/company} supplies the adapter, so Support never imports {@code User} or
 * {@code Branch} directly.
 *
 * <p>Every method is company-scoped explicitly.
 */
public interface TicketDirectoryPort {

    /** Flattened identity of a user, enough to validate an assignee and label a caller. */
    record UserRef(UUID userId, UUID companyId, String fullName, String email) {
    }

    /** The user, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<UserRef> findUser(UUID userId, UUID companyId);

    /** Whether this branch exists within this company. */
    boolean branchExists(UUID branchId, UUID companyId);

    /** The branch this user is placed at ({@code users.branch_id}), if any. */
    Optional<UUID> branchOfUser(UUID userId, UUID companyId);

    /** The branch that names this user as its manager, if any. */
    Optional<UUID> branchManagedBy(UUID userId, UUID companyId);

    /** The user this branch names as its manager ({@code branches.manager_id}), if any —
     *  used by {@code ShipmentSlaSweepJob} to auto-assign a breach ticket. */
    Optional<UUID> managerOfBranch(UUID branchId, UUID companyId);

    /** Every active company id, for {@code ShipmentSlaSweepJob} to iterate. */
    List<UUID> listActiveCompanyIds();

    /** This company's shipment-lifecycle SLA configuration, from {@code company_settings}. */
    ShipmentSlaConfig shipmentSlaSettings(UUID companyId);
}
