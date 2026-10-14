package com.courier.modules.pickup.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a pickup request. {@code REQUESTED -> ASSIGNED -> ACCEPTED -> OUT_FOR_PICKUP ->
 * PICKED_UP -> BOOKED}. A partner may reject an assignment (back to REQUESTED) or fail a visit
 * (terminal FAILED); staff may cancel until the goods are collected.
 */
public enum PickupStatus {
    REQUESTED, ASSIGNED, ACCEPTED, OUT_FOR_PICKUP, PICKED_UP, BOOKED, FAILED, CANCELLED;

    private static final Map<PickupStatus, Set<PickupStatus>> TRANSITIONS = Map.of(
            REQUESTED, EnumSet.of(ASSIGNED, CANCELLED),
            ASSIGNED, EnumSet.of(ACCEPTED, REQUESTED, ASSIGNED, CANCELLED),
            ACCEPTED, EnumSet.of(OUT_FOR_PICKUP, REQUESTED, ASSIGNED, CANCELLED),
            OUT_FOR_PICKUP, EnumSet.of(PICKED_UP, FAILED, CANCELLED),
            PICKED_UP, EnumSet.of(BOOKED),
            BOOKED, EnumSet.noneOf(PickupStatus.class),
            FAILED, EnumSet.noneOf(PickupStatus.class),
            CANCELLED, EnumSet.noneOf(PickupStatus.class));

    public boolean canMoveTo(PickupStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    /** Still open: the partner or staff can act on it. */
    public boolean isOpen() {
        return this != BOOKED && this != FAILED && this != CANCELLED;
    }
}
