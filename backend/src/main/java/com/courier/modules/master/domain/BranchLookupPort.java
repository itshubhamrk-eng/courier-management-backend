package com.courier.modules.master.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What Master Data needs to know about branches, and nothing more — the two endpoints of
 * a route.
 *
 * <p>Master owns the interface; {@code modules/company} supplies the adapter. The same
 * seam auth uses for companies ({@code CompanyDirectoryPort}) and Finance for wallets
 * ({@code BranchDirectoryPort}), and for the same reason: a route has no business holding
 * a {@code Branch}, and the dependency arrow must point at this module's own abstraction
 * rather than into another feature's entities.
 *
 * <p>Deliberately <b>not</b> a reuse of Finance's {@code BranchDirectoryPort}. Importing
 * it would make Master depend on Finance to talk about branches, which is a worse arrow
 * than the small duplication of a three-field record.
 *
 * <p>The company is passed explicitly on every call. An adapter must never answer across
 * companies, so a caller that forgot to bind a company gets nothing rather than everything.
 */
public interface BranchLookupPort {

    /** Identity of one branch, flattened: enough to label a route and to refuse an inactive one. */
    record BranchRef(UUID branchId, String branchCode, String branchName, boolean active) {
    }

    /** The branch, if it exists within this company. Empty for a foreign or unknown id. */
    Optional<BranchRef> findBranch(UUID branchId, UUID companyId);

    /**
     * The branches of this company among {@code branchIds}, keyed by id.
     *
     * <p>Exists so that listing a page of routes costs one query rather than two per row.
     * Ids that do not belong to the company are simply absent from the map — the caller
     * shows no name rather than being told one exists.
     */
    Map<UUID, BranchRef> findBranches(Collection<UUID> branchIds, UUID companyId);
}
