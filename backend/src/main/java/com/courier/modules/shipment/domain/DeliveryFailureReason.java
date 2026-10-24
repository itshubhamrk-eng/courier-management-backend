package com.courier.modules.shipment.domain;

/** Why a delivery attempt failed. {@link #OTHER} needs remarks. */
public enum DeliveryFailureReason {
    CUSTOMER_REFUSED,
    ADDRESS_NOT_FOUND,
    CUSTOMER_UNAVAILABLE,
    RESCHEDULE_REQUESTED,
    PAYMENT_NOT_READY,
    OTHER
}
