package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TicketMessageResponse", description = "One conversation entry — a reply or an internal note")
public record TicketMessageResponse(
        UUID id,
        UUID ticketId,
        UUID authorUserId,
        String body,
        boolean internalNote,
        Instant createdAt
) {
}
