package com.courier.modules.charge.domain;

/**
 * What {@link ChargeSetting#getCommissionValue()} means. Configuration only at this stage
 * — nothing in Shipment Booking reads it yet; a future calculation engine will.
 */
public enum CommissionType {
    AMOUNT,
    PERCENTAGE
}
