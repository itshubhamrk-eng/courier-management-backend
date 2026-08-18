package com.courier.modules.followup.application.command;

import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpType;

import java.time.Instant;
import java.util.UUID;

public record CreateFollowUpCommand(
        UUID branchId,
        FollowUpType referenceType,
        UUID referenceId,
        UUID customerId,
        UUID shipmentId,
        UUID assignedUserId,
        String title,
        String description,
        FollowUpType followUpType,
        FollowUpPriority priority,
        Instant dueDate
) {
}
