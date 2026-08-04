package com.courier.modules.master.domain;

/**
 * Unit a route's distance is expressed in.
 *
 * <p>Only {@code KM} exists today — every distance in the system is kilometres — but the
 * column is named rather than assumed so a future unit is a new enum constant, not a
 * migration that renames {@code distance_km} out from under every existing row.
 */
public enum DistanceUnit {
    KM
}
