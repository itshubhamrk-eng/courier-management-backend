package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A customer-care ticket raised against this shipment, as shown on the public tracking
 * page's verified detail view. Deliberately excludes staff-facing fields present on the
 * authenticated {@code Ticket} entity — assignee, internal notes, SLA due/breach timestamps —
 * the same "no more than the customer would reasonably be told" boundary {@code
 * PublicTrackResponse} draws for the shipment itself. {@code history} is the same
 * status-update timeline the shipment gets, sourced from {@code TicketStatusHistory}.
 */
@Schema(name = "PublicTrackTicketResponse")
public record PublicTrackTicketResponse(
        String ticketNumber,
        String subject,
        String status,
        String priority,
        Instant createdAt,
        Instant resolvedAt,
        List<PublicTicketEventResponse> history
) {
}
