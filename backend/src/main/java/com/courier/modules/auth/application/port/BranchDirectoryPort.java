package com.courier.modules.auth.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * The auth module's view of branches — the same seam {@link CompanyDirectoryPort} uses
 * for companies. Auth needs exactly two facts to run "login as branch" impersonation —
 * does this branch exist within the caller's own company, and is it still active — and
 * nothing about its address, capabilities or wallet.
 *
 * <p>The only implementation is in {@code modules/company}.
 */
public interface BranchDirectoryPort {

    /** The branch, if it exists within {@code companyId}. Empty for a foreign or unknown id. */
    Optional<BranchRef> findById(UUID branchId, UUID companyId);

    /**
     * Minimal projection of a branch.
     *
     * @param id     the branch's own id
     * @param code   {@code branchCode}, for display/audit
     * @param name   display name, carried into the impersonation session
     * @param active whether the branch may be logged into right now
     */
    record BranchRef(UUID id, String code, String name, boolean active) {
    }
}
