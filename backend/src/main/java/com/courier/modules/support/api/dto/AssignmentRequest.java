package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "AssignmentRequest", description = "Body of assign/reassign/escalate")
public record AssignmentRequest(
        @Schema(description = "Required for assign/reassign; optional for escalate") UUID assigneeUserId,
        @Size(max = 1000) String remarks
) {
}
