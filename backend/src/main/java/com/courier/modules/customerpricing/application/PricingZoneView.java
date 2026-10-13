package com.courier.modules.customerpricing.application;

import com.courier.modules.customerpricing.domain.PricingZone;

import java.util.List;
import java.util.UUID;

/** A zone with its locations resolved to display names, plus whether it has a rate card yet. */
public record PricingZoneView(PricingZone zone, String customerName, List<LocationView> locations, boolean hasRate) {

    public record LocationView(UUID id, UUID districtId, String districtName, UUID cityId, String cityName) {
    }
}
