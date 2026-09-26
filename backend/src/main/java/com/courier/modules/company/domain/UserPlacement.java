package com.courier.modules.company.domain;

/**
 * Where a user sits in the company — mutually exclusive for the search filter, matching
 * how the Users screen splits its listing into tabs. Not stored; derived at query time
 * from {@code users.branch_id} and, when set, the referenced branch's
 * {@link BranchType}. {@code users.hub_id} plays no part here — it is a legacy column
 * with no FK and no writer; a hub placement is a branch whose {@code branchType} is
 * {@code HUB}, not a separate hub_id, same as branch staffing generally.
 */
public enum UserPlacement {

    /** No branch — a company-level user (e.g. COMPANY_ADMIN, finance staff). */
    COMPANY,

    /** Placed at a channel-partner branch ({@link BranchType#CP}). */
    CP,

    /** Placed at a regular branch ({@link BranchType#BRANCH}). */
    BRANCH,

    /** Placed at a hub ({@link BranchType#HUB}). */
    HUB
}
