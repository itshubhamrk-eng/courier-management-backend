package com.courier.modules.customerpricing.application;

import com.courier.modules.customerpricing.domain.ZoneStatus;

import java.util.List;
import java.util.UUID;

/**
 * Pricing zones of business customers. {@code COMPANY_ADMIN} writes; any authenticated user
 * of the company reads (booking needs to resolve a zone). The company is the caller's.
 */
public interface PricingZoneService {

    /** @param locations may be empty; each entry is a district, or a city inside one. */
    PricingZoneView create(UUID customerId, String zoneName, String description, List<LocationInput> locations);

    PricingZoneView update(UUID zoneId, String zoneName, String description, Long expectedVersion);

    PricingZoneView getById(UUID zoneId);

    /** All filters optional. Not paged: a customer has a handful of zones. */
    List<PricingZoneView> search(UUID customerId, ZoneStatus status, String search);

    PricingZoneView addLocation(UUID zoneId, LocationInput location);

    PricingZoneView removeLocation(UUID zoneId, UUID locationId);

    PricingZoneView activate(UUID zoneId);

    PricingZoneView deactivate(UUID zoneId);

    /** Soft-deletes the zone, its locations and its rate card. */
    void delete(UUID zoneId);

    /**
     * The ACTIVE zone of {@code customerId} covering a destination — an exact city match wins
     * over a whole-district one — or empty if none does.
     */
    java.util.Optional<PricingZoneView> resolve(UUID customerId, UUID districtId, UUID cityId);

    record LocationInput(UUID districtId, UUID cityId) {
    }
}
