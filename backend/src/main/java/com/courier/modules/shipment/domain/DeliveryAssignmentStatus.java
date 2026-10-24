package com.courier.modules.shipment.domain;

public enum DeliveryAssignmentStatus {
    ASSIGNED,
    DELIVERED,
    /** Last attempt failed (V135); a new DRS reassigns it back to ASSIGNED. */
    FAILED
}
