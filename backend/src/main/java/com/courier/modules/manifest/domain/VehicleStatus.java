package com.courier.modules.manifest.domain;

/**
 * Operational state of a fleet vehicle. Distinct from {@code Vehicle.active} — this is
 * "what the vehicle is doing right now", not "is the record enabled" (that's the
 * activate/deactivate toggle every other module in this project uses).
 */
public enum VehicleStatus {
    AVAILABLE,
    IN_USE,
    MAINTENANCE,
    INACTIVE
}
