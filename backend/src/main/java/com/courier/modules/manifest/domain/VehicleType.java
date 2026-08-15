package com.courier.modules.manifest.domain;

/**
 * Fixed vehicle class for the fleet entity. Unlike {@code master.domain.VehicleType}
 * (a company-editable catalogue table for Rate Master's own vehicle-class needs), this
 * is a closed, small set fixed at the fleet-record level — the two are deliberately
 * independent, no shared code or FK.
 */
public enum VehicleType {
    BIKE,
    SCOOTER,
    AUTO,
    VAN,
    PICKUP,
    TRUCK,
    TEMPO,
    OTHER
}
