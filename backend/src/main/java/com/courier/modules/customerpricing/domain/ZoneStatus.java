package com.courier.modules.customerpricing.domain;

/** Lifecycle of a pricing zone or its rate card. Shared: both are only ever withdrawn, never hard-deleted. */
public enum ZoneStatus {
    ACTIVE,
    INACTIVE
}
