package com.courier.modules.shipment.domain;

/**
 * How the shipment reaches the receiver at the destination branch. {@code DOOR} — carried
 * to the receiver's address, and may carry a manual {@code ShipmentCharge
 * .doorDeliveryCharge} (deliberately GST-free, same treatment as {@code
 * appointmentDeliveryCharge}). {@code OFFICE} — the receiver collects from the branch
 * counter; never charged extra, regardless of what was typed — see
 * {@code ShipmentServiceImpl.copyCharge}.
 */
public enum DeliveryType {
    DOOR,
    OFFICE
}
