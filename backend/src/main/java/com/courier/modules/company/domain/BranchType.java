package com.courier.modules.company.domain;

/**
 * What a branch does in the network. The type is descriptive, not a hard capability gate —
 * the {@code allow*} service flags on the branch are the operative switches. A
 * {@code CP} whose {@code allowDelivery} is somehow true still would not be wrong to
 * model; the type is the intent, the flags are the rule.
 */
public enum BranchType {

    /** Channel Partner — a third-party agent operating under the company's network. */
    CP,

    /** A regular company-operated branch (booking, delivery, or both). */
    BRANCH,

    /** Aggregation/sorting point serving a cluster of branches. */
    HUB,

    /** Third-party vendor. */
    VENDOR
}
