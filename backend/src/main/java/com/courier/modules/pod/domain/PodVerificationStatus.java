package com.courier.modules.pod.domain;

/**
 * Outcome of one POD verification row. A delivery-app upload ({@code verify()}) always lands
 * {@link #PENDING} — the AI score/reasons are informational only, a human always makes the
 * PASS/FAIL call via {@code POST /shipments/{id}/pod/review}. A company-direct upload
 * ({@code uploadByCompany()}) skips AI entirely and always writes {@link #PASS} — the company
 * vouching for it directly.
 */
public enum PodVerificationStatus {
    /** Awaiting a human Approve/Reject decision — every delivery-app POD upload starts here. */
    PENDING,
    /** Approved — either by a human reviewer, or a company-direct upload. Delivery commission
     *  credits on this transition. */
    PASS,
    /** Rejected by a human reviewer — the delivery user must capture a new POD. */
    FAIL
}
