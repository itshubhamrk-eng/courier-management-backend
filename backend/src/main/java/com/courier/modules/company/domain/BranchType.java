package com.courier.modules.company.domain;

/**
 * What a branch does in the network. The type is descriptive, not a hard capability gate —
 * the {@code allow*} service flags on the branch are the operative switches. A
 * {@code BOOKING_BRANCH} whose {@code allowDelivery} is somehow true still would not be
 * wrong to model; the type is the intent, the flags are the rule.
 */
public enum BranchType {

    /** The company's principal office. */
    HEAD_OFFICE,

    /** Oversees a cluster of branches in a region. */
    REGIONAL_OFFICE,

    /** Takes bookings only. */
    BOOKING_BRANCH,

    /** Delivers only. */
    DELIVERY_BRANCH,

    /** Both books and delivers — the common high-street office. */
    BOOKING_DELIVERY_BRANCH
}
