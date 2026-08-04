package com.courier.modules.finance.domain;

/**
 * Operational state of a branch wallet.
 *
 * <p>Only {@link #ACTIVE} moves money. The other three all refuse credits and debits: a
 * wallet that is not operational must not silently accept a recharge the branch cannot
 * then spend, and must not be debited by a booking that should have been blocked earlier.
 */
public enum WalletStatus {

    ACTIVE,

    /** Switched off by the company; balance retained, no transactions accepted. */
    INACTIVE,

    /** Held during a dispute or investigation. */
    SUSPENDED,

    /** Terminal. Set when the branch is retired and the balance has been settled. */
    CLOSED;

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
