package com.courier.modules.districtfreight.domain;

/** Which destination a rate row is keyed by. Booking tries DISTRICT first, then STATE. */
public enum DistrictFreightLevel {
    DISTRICT, STATE
}
