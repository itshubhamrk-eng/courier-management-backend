package com.courier.modules.pdp.domain;

/** Which job a Pickup-Delivery Partner rate card pays for. */
public enum PdpRateType {
    /** Paid when the partner collects a shipment from the consignor. */
    PICKUP,
    /** Paid when the partner delivers a shipment to the consignee. */
    DELIVERY
}
