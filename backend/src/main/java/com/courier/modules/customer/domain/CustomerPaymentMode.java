package com.courier.modules.customer.domain;

/** How a customer normally pays for a booking. Defaults to {@link #PAID}. */
public enum CustomerPaymentMode {
    CREDIT,
    PAID,
    TOPAY
}
