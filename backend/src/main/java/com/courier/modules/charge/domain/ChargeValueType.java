package com.courier.modules.charge.domain;

/** What {@link ChargeSetting#getChargeValue()} means. */
public enum ChargeValueType {
    /** A flat monetary amount. */
    AMOUNT,
    /** A percentage, {@code [0, 100]}, applied against a base the calculation engine defines later. */
    PERCENTAGE
}
