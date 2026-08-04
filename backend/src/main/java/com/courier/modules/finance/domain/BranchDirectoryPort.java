package com.courier.modules.finance.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What Finance needs to know about branches — and nothing more.
 *
 * <p>The same seam auth uses for companies ({@code CompanyDirectoryPort}, implemented by
 * {@code CompanyDirectory}). Finance owns the interface; {@code modules/company}
 * supplies the adapter. That keeps the dependency arrow pointing at this module's own
 * abstraction instead of reaching into another feature's entities and repositories, which
 * the architecture forbids: a wallet has no business holding a {@code Branch}.
 *
 * <p>Every method is company-scoped explicitly. Finance never calls this without a bound
 * company, and the adapter must never answer across companies.
 */
public interface BranchDirectoryPort {

    /**
     * Identity of one branch, flattened. Carries only what a wallet view shows: the code
     * and name that label a statement, and whether the branch is still operational.
     */
    record BranchRef(UUID branchId, UUID companyId, String branchCode, String branchName,
                     boolean active) {
    }

    /** The branch, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<BranchRef> findBranch(UUID branchId, UUID companyId);

    /** The branch this user is placed at ({@code users.branch_id}), if any. */
    Optional<UUID> branchOfUser(UUID userId, UUID companyId);

    /** The branch that names this user as its manager, if any. */
    Optional<UUID> branchManagedBy(UUID userId, UUID companyId);
}
