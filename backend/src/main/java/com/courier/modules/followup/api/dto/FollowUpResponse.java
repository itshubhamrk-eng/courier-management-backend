package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpStatus;
import com.courier.modules.followup.domain.FollowUpType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Flat, ids only — same convention {@code TicketResponse} uses; the frontend carries
 *  its own directories for labels. */
@Schema(name = "FollowUpResponse", description = "A follow-up task")
public record FollowUpResponse(
        UUID id,
        UUID companyId,
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
        FollowUpStatus status,
        Instant dueDate,
        Instant nextFollowUpDate,
        boolean overdue,
        Instant completedAt,
        UUID completedBy,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
