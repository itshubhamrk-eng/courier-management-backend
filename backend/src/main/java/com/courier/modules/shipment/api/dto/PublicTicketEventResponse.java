package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One entry of a ticket's public status-update timeline — status and timestamp only, same
 *  "no more than the customer would reasonably be told" boundary {@link
 *  PublicTrackEventResponse} draws for the shipment timeline: no staff remarks, no who
 *  changed it. See {@link PublicTrackTicketResponse}. */
@Schema(name = "PublicTicketEventResponse", description = "One entry of a ticket's public status-update timeline")
public record PublicTicketEventResponse(String status, Instant changedAt) {
}
