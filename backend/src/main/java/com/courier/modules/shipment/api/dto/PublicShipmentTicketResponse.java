package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Just enough for the tracking page to show a reference number. */
@Schema(name = "PublicShipmentTicketResponse")
public record PublicShipmentTicketResponse(String ticketNumber) {
}
