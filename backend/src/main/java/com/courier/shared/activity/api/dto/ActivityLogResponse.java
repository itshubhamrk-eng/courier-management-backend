package com.courier.shared.activity.api.dto;

import com.courier.shared.activity.domain.ActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ActivityLogResponse")
public record ActivityLogResponse(
        UUID id,
        UUID companyId,
        UUID userId,
        String username,
        @Schema(description = "The user's first and last name, looked up at read time; null if the user is gone.")
        String userFullName,
        @Schema(description = "The user's branch, looked up at read time; null for users without one.")
        String branchName,
        String module,
        String submodule,
        String action,
        String entityType,
        String entityId,
        String description,
        String oldValue,
        String newValue,
        String ipAddress,
        String device,
        String browser,
        String os,
        UUID sessionId,
        String requestMethod,
        String apiEndpoint,
        ActivityStatus status,
        String errorMessage,
        Instant occurredAt
) {
}
