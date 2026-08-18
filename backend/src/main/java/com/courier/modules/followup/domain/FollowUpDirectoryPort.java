package com.courier.modules.followup.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Follow-up Management needs to know about users and branches — and nothing more.
 * Same seam {@code support.domain.TicketDirectoryPort}/{@code finance.domain
 * .BranchDirectoryPort} draw: this module owns the interface, {@code modules/company}
 * supplies the adapter, so Follow-up never imports {@code User} or {@code Branch}
 * directly.
 *
 * <p>Every method is company-scoped explicitly.
 */
public interface FollowUpDirectoryPort {

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

    /** Every active company id, for {@code FollowUpSweepJob} to iterate. */
    List<UUID> listActiveCompanyIds();
}
