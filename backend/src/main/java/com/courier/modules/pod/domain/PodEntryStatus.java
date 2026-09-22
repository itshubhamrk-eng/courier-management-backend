package com.courier.modules.pod.domain;

/**
 * The paper-register-style "Status" column on a company-level POD upload (delivery date,
 * delivered by, POD date/time, status, remark) — independent of both {@link
 * PodVerificationStatus} (the AI/review outcome) and the shipment's own DELIVERED status
 * machine. Purely descriptive: recording it here never itself moves the shipment.
 */
public enum PodEntryStatus {
    DELIVERED,
    NOT_DELIVERED,
    RETURNED,
    RTO
}
