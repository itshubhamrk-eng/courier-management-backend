package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpStatus;
import com.courier.modules.followup.domain.FollowUpType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "FollowUpSearchRequest", description = "Follow-up search filters")
public record FollowUpSearchRequest(
        FollowUpStatus status,
        FollowUpPriority priority,
        FollowUpType type,
        UUID assignedUser,
        Instant dueDate,
        @Schema(description = "true narrows to overdue (past due date, not completed/cancelled)") boolean overdue,
        UUID customer,
        UUID shipment,
        UUID branch,
        String search
) {
}
