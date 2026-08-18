package com.courier.modules.dashboard.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What the dashboard needs to know about branches — just enough to label the Top Routes
 * card. The same seam every other module uses ({@code finance.BranchDirectoryPort},
 * {@code crossing.CrossingBranchDirectoryPort}): this module owns the interface, {@code
 * modules/company} supplies the adapter, so the dashboard never imports {@code Branch} or
 * its repository directly.
 */
public interface DashboardBranchDirectoryPort {

    record BranchRef(UUID branchId, String branchCode, String branchName) {
    }

    /** The requested branches that exist within this company, keyed by id. Unknown or
     *  foreign ids are simply absent — never an error, the same batch-lookup shape
     *  {@code master.domain.BranchLookupPort.findBranches} already uses. */
    Map<UUID, BranchRef> findBranches(Collection<UUID> branchIds, UUID companyId);
}
