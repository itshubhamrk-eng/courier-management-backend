package com.courier.modules.master.domain;

/**
 * Whether a master-data row may be used on new work.
 *
 * <p>Deactivating withdraws a row from the pickers a booking clerk sees without deleting
 * it: shipments, manifests and rate cards that already reference it keep resolving. This
 * is the same two-state shape {@code BranchStatus} uses, repeated here rather than shared
 * because the two modules must be free to diverge — a branch could grow a SUSPENDED state
 * without dragging every master list into it.
 */
public enum MasterStatus {
    ACTIVE, INACTIVE;

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
