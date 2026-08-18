package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpPriority;
import com.courier.modules.followup.domain.FollowUpType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "CreateFollowUpRequest", description = "Create a follow-up task")
public record CreateFollowUpRequest(

        @Schema(description = "Defaults to the caller's own branch; COMPANY_ADMIN/HUB_MANAGER may name any branch")
        UUID branchId,

        @Schema(description = "What referenceId points at; defaults to followUpType when omitted") FollowUpType referenceType,
        UUID referenceId,
        UUID customerId,
        UUID shipmentId,
        UUID assignedUserId,

        @NotBlank @Size(max = 200) String title,
        @Size(max = 8000) String description,

        @Schema(description = "Defaults to GENERAL when omitted") FollowUpType followUpType,
        @Schema(description = "Defaults to MEDIUM when omitted") FollowUpPriority priority,

        @NotNull Instant dueDate
) {
}
