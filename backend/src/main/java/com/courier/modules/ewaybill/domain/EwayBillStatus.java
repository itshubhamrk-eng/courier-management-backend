package com.courier.modules.ewaybill.domain;

/**
 * Lifecycle of one E-Way Bill row.
 *
 * <p>{@code NOT_REQUIRED}/{@code REQUIRED} describe intent before any data has been typed
 * — a placeholder row a booking screen can create the instant the invoice value crosses
 * the mandatory threshold, before the operator has filled in a number. {@code CANCELLED}
 * is terminal: an E-Way Bill is withdrawn, never deleted.
 */
public enum EwayBillStatus {
    NOT_REQUIRED,
    REQUIRED,
    PENDING,
    UPLOADED,
    VALIDATED,
    INVALID,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CANCELLED;
    }

    /**
     * @throws com.courier.shared.exception.BusinessRuleException {@code next} is not a
     *         legal move from this status
     */
    public void requireCanTransitionTo(EwayBillStatus next) {
        if (!canTransitionTo(next)) {
            throw new com.courier.shared.exception.BusinessRuleException(
                    "E-Way Bill cannot move from %s to %s.".formatted(this, next));
        }
    }

    public boolean canTransitionTo(EwayBillStatus next) {
        if (this == next) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        return switch (this) {
            case NOT_REQUIRED, REQUIRED ->
                    next == PENDING || next == UPLOADED || next == VALIDATED || next == CANCELLED;
            case PENDING -> next == UPLOADED || next == VALIDATED || next == INVALID || next == CANCELLED;
            case UPLOADED -> next == VALIDATED || next == INVALID || next == CANCELLED;
            case VALIDATED -> next == EXPIRED || next == CANCELLED;
            case INVALID -> next == UPLOADED || next == VALIDATED || next == CANCELLED;
            case EXPIRED -> next == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
