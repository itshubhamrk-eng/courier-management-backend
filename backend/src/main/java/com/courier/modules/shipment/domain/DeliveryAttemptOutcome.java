package com.courier.modules.shipment.domain;

/** What a failed attempt did to the shipment. */
public enum DeliveryAttemptOutcome {
    /** Back at the delivery branch / hub, eligible for the next DRS. */
    RETRY,
    /** Attempt limit reached: shipment marked RETURNED (return to origin). */
    RTO
}
