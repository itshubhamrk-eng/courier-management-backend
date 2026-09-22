package com.courier.modules.crossing.domain;

/** What went wrong with a shipment at a hub. Raising one never changes {@code
 *  Shipment.status} — see {@code ShipmentException}'s own doc for why. */
public enum ShipmentExceptionType {
    MISSING,
    DAMAGED,
    SHORT,
    WRONG_DESTINATION,
    MISROUTED,
    ON_HOLD
}
