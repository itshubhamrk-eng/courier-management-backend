package com.courier.shared.activity.api.dto;

import com.courier.shared.activity.domain.ActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Query params for {@code GET /api/v1/activity-logs}. Every field optional. */
@Schema(name = "ActivityLogSearchRequest")
public record ActivityLogSearchRequest(
        UUID userId,
        String module,
        String action,
        String entityType,
        String entityId,
        ActivityStatus status,
        UUID sessionId,
        Instant dateFrom,
        Instant dateTo,
        String search
) {
}
