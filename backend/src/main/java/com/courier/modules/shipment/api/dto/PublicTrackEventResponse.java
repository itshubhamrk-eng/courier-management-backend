package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One entry of a public tracking timeline — status and timestamp only, no branch,
 *  vehicle or user identifiers (those are internal operational detail, not something an
 *  unauthenticated caller should see). See {@link PublicTrackResponse}. */
@Schema(name = "PublicTrackEventResponse", description = "One entry of a shipment's public tracking timeline")
public record PublicTrackEventResponse(ShipmentStatus status, Instant changedAt) {
}
