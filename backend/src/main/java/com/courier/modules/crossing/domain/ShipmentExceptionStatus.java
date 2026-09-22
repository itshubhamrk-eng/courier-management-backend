package com.courier.modules.crossing.domain;

/** Lifecycle of one raised exception — a human closes it explicitly; nothing auto-resolves
 *  it, the same "a human always decides" rule POD Verification's {@code PENDING} follows. */
public enum ShipmentExceptionStatus {
    OPEN,
    RESOLVED
}
