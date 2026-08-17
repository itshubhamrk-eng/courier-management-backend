package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TicketAttachmentResponse", description = "A file attached to a ticket, at creation or on a reply")
public record TicketAttachmentResponse(
        UUID id,
        UUID ticketId,
        UUID messageId,
        String assetUrl,
        String filename,
        String contentType,
        long sizeBytes,
        UUID uploadedByUserId,
        Instant createdAt
) {
}
