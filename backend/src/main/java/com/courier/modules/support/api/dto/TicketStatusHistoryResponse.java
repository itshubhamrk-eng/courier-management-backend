package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TicketStatusHistoryResponse", description = "One status transition")
public record TicketStatusHistoryResponse(
        UUID id,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        UUID changedByUserId,
        String remarks,
        Instant createdAt
) {
}
