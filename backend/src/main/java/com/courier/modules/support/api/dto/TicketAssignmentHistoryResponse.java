package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketAssignmentAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TicketAssignmentHistoryResponse", description = "One assign/reassign/escalate action")
public record TicketAssignmentHistoryResponse(
        UUID id,
        UUID assignedToUserId,
        UUID assignedByUserId,
        TicketAssignmentAction action,
        String remarks,
        Instant createdAt
) {
}
