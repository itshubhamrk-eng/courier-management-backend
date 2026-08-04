package com.courier.modules.company.domain;

/**
 * Whether a branch is operating. Deactivating withdraws it from booking/delivery
 * assignment without deleting it — shipments and history that reference it survive.
 */
public enum BranchStatus {
    ACTIVE, INACTIVE;

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
