package com.courier.modules.customerbilling.domain;

/** A new invoice waits for an admin; approving it is what sends it to the customer. */
public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED
}
