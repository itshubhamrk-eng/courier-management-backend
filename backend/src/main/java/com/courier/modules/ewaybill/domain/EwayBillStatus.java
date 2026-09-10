package com.courier.modules.ewaybill.domain;

/**
 * Lifecycle of one E-Way Bill row, matching the auto-generation workflow end to end:
 * booking decides {@code REQUIRED}/{@code NOT_REQUIRED}, Shipment Booking then drives
 * {@code PART_A_PENDING} -&gt; {@code PART_A_GENERATED} (or {@code FAILED}), and Manifest
 * dispatch (vehicle assignment) drives {@code PART_B_PENDING} -&gt; {@code GENERATED}
 * (or {@code FAILED}). {@code EXPIRED} means the provider's own validity window has
 * lapsed; retrying from it requests a fresh E-Way Bill number, same as retrying a
 * {@code FAILED} row whose Part-A never succeeded. {@code CANCELLED} is terminal.
 *
 * <p>Which stage a {@code FAILED}/{@code EXPIRED} row retries into is not encoded in the
 * status itself — {@code EwayBillServiceImpl.retry} decides by checking whether the row
 * already carries a provider-issued {@code ewayBillNumber}.
 */
public enum EwayBillStatus {
    NOT_REQUIRED,
    REQUIRED,
    PART_A_PENDING,
    PART_A_GENERATED,
    PART_B_PENDING,
    GENERATED,
    FAILED,
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
            case NOT_REQUIRED, REQUIRED -> next == PART_A_PENDING || next == CANCELLED;
            case PART_A_PENDING -> next == PART_A_GENERATED || next == FAILED || next == CANCELLED;
            case PART_A_GENERATED -> next == PART_B_PENDING || next == EXPIRED || next == CANCELLED;
            case PART_B_PENDING -> next == GENERATED || next == FAILED || next == CANCELLED;
            case GENERATED -> next == EXPIRED || next == CANCELLED;
            // Retryable from either stage: no ewayBillNumber yet means Part-A never
            // succeeded (-> PART_A_PENDING); one already issued means only Part-B was
            // outstanding (-> PART_B_PENDING). See EwayBillServiceImpl.retry.
            case FAILED -> next == PART_A_PENDING || next == PART_B_PENDING || next == CANCELLED;
            case EXPIRED -> next == PART_A_PENDING || next == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
