package com.courier.modules.districtfreight.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What District Level Freight needs to know about branches — the "From Station" end of a
 * rate row — and nothing more.
 *
 * <p>District Level Freight owns this interface; {@code modules/company} supplies the
 * adapter. Deliberately its own port, not a reuse of Master's own
 * {@code com.courier.modules.master.domain.BranchLookupPort} or Finance's
 * {@code BranchDirectoryPort} — importing either would make this module depend on
 * another feature's abstraction to talk about branches, the same reasoning Master's own
 * javadoc gives for not reusing Finance's.
 */
public interface BranchLookupPort {

    record BranchRef(UUID branchId, String branchCode, String branchName, boolean active) {
    }

    Optional<BranchRef> findBranch(UUID branchId, UUID companyId);

    Map<UUID, BranchRef> findBranches(Collection<UUID> branchIds, UUID companyId);

    /**
     * Resolves a "From Station" cell from an Excel import against either the branch code
     * or the branch name, case-insensitively — the sheet names a station by its familiar
     * label, not its id. Code is tried first since it's the stabler identifier.
     */
    Optional<BranchRef> findBranchByLabel(String label, UUID companyId);
}
