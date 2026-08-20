package com.courier.modules.pod.domain;

/**
 * Outcome of one POD Auto Verification run. There is no {@code PENDING}/processing state —
 * verification runs synchronously against the uploaded photo/signature and always resolves
 * to one of these three before the request returns; an unavailable AI provider resolves to
 * {@link #REVIEW}, never left unresolved.
 */
public enum PodVerificationStatus {
    /** High confidence — the frontend may offer "Complete Delivery" directly. */
    PASS,
    /** Medium confidence, or a safety signal (duplicate/tampering) fired — a human must
     *  approve or reject via {@code POST /shipments/{id}/pod/review} before delivery can
     *  be completed. */
    REVIEW,
    /** Low confidence — delivery must not be completed from this POD; the delivery user
     *  must capture a new one. */
    FAIL
}
