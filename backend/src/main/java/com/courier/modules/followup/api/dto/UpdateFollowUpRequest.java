package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "UpdateFollowUpRequest", description = "Full replacement update of a non-terminal follow-up")
public record UpdateFollowUpRequest(
        UUID branchId,
        FollowUpType referenceType,
        UUID referenceId,
        UUID customerId,
        UUID shipmentId,

        @NotBlank @Size(max = 200) String title,
        @Size(max = 8000) String description,

        FollowUpType followUpType,
        FollowUpPriority priority,

        @NotNull Instant dueDate,

        @Schema(description = "Optimistic-lock guard — the version last read by the caller") Long version
) {
}
