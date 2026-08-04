package com.courier.modules.finance.domain;

/**
 * State of the gateway payment behind a ledger entry. Null on entries that are not a
 * payment at all (a manual credit, a booking debit).
 *
 * <p>Only {@link #SUCCESS} entries have moved the balance. A {@link #PENDING} row is the
 * intent recorded when a gateway order is opened; it is settled — or abandoned — when the
 * gateway's signed confirmation arrives.
 */
public enum PaymentStatus {

    /** Order created at the gateway, payment not yet confirmed. No balance change. */
    PENDING,

    /** Signature verified. This is the only status that credits the wallet. */
    SUCCESS,

    /** The gateway reported failure, or verification was refused. No balance change. */
    FAILED,

    /** A previously successful payment was refunded; the reversal is its own entry. */
    REFUNDED
}
