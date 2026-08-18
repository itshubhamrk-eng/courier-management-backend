package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpHistoryAction;
import com.courier.modules.followup.domain.FollowUpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "FollowUpHistoryResponse", description = "One timeline entry: creation, status change, reschedule, assignment or note")
public record FollowUpHistoryResponse(
        UUID id,
        FollowUpHistoryAction action,
        FollowUpStatus fromStatus,
        FollowUpStatus toStatus,
        Instant previousDueDate,
        Instant newDueDate,
        UUID assignedToUserId,
        String note,
        UUID changedByUserId,
        Instant createdAt
) {
}
