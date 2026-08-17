package com.courier.modules.crossing.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What Crossing needs to know about branches — and nothing more. The same seam Finance
 * uses ({@code BranchDirectoryPort}): the consuming module owns the interface, {@code
 * modules/company} supplies the adapter, so Crossing never imports {@code Branch} or its
 * repository.
 *
 * <p>Deliberately not {@code BranchService.getById} — that method 404s a branch the
 * caller is not personally placed at or managing (see {@code BranchServiceImpl
 * .requireVisible}), which is correct for a branch directory screen but wrong here: a
 * booking desk names a crossing branch that is, by definition, not their own branch,
 * the same way {@code deliveryBranchId} is never the caller's own branch either.
 */
public interface CrossingBranchDirectoryPort {

    record BranchRef(UUID branchId, UUID companyId, String branchCode, String branchName, boolean active) {
    }

    /** The branch, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<BranchRef> findBranch(UUID branchId, UUID companyId);
}
