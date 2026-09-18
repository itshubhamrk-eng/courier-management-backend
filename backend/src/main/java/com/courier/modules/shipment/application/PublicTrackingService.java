package com.courier.modules.shipment.application;

import com.courier.modules.shipment.api.dto.PublicTrackResponse;

/**
 * Track Shipment for an unauthenticated caller (the login page's "Track Shipment" link,
 * and any other public tracking page). Deliberately separate from {@link ShipmentService}:
 * every method there is gated by {@code @PreAuthorize} on an authenticated company user,
 * and this is the one lookup in the module that must work without either — keeping it in
 * its own class makes that boundary a file, not a missing annotation to notice.
 */
public interface PublicTrackingService {

    /** Looked up cross-company by tracking or shipment number. */
    PublicTrackResponse track(String number);
}
