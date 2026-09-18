package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Redacted projection of a shipment for the public (no-login) Track Shipment page —
 * origin/destination city, status and timeline only. Deliberately excludes address,
 * contact, pricing and every internal identifier (branch/vehicle/user) a JWT-authenticated
 * caller would get from {@link ShipmentResponse}; anyone with the AWB can call this
 * endpoint, so it must never carry anything more sensitive than what's printed on the
 * parcel's own shipping label.
 */
@Schema(name = "PublicTrackResponse", description = "Redacted shipment projection for public tracking")
public record PublicTrackResponse(
        String trackingNumber,
        String shipmentNumber,
        ShipmentStatus status,
        LocalDate bookingDate,
        LocalDate expectedDeliveryDate,
        String fromCity,
        String toCity,
        List<PublicTrackEventResponse> timeline
) {
}
