package com.courier.modules.pdp.domain;

/** Lifecycle of a PDP rate card: withdrawn, never hard-deleted from the user's side. */
public enum PdpRateStatus {
    ACTIVE,
    INACTIVE
}
