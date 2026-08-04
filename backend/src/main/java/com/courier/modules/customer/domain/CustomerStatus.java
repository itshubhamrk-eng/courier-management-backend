package com.courier.modules.customer.domain;

/**
 * Whether a customer (or one of their addresses) is usable. Deactivating withdraws
 * it from the booking pickers without deleting it — past shipments that reference
 * it survive.
 */
public enum CustomerStatus {
    ACTIVE, INACTIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
