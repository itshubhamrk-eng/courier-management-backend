package com.courier.modules.manifest.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What Manifest needs to know about branches — and nothing more. The same seam Finance
 * and Crossing use ({@code BranchDirectoryPort}/{@code CrossingBranchDirectoryPort}):
 * the consuming module owns the interface, {@code modules/company} supplies the adapter.
 *
 * <p>Added for Hub Operations (2026-09-21): {@code dispatch()} needs to know whether the
 * booking branch is a hub, to decide whether the out-scan gate applies.
 */
public interface BranchDirectoryPort {

    record BranchRef(UUID branchId, UUID companyId, String branchType, boolean active) {
    }

    /** The branch, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<BranchRef> findBranch(UUID branchId, UUID companyId);
}
