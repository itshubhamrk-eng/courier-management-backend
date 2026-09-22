package com.courier.modules.auth.api.dto;

import com.courier.shared.activity.api.dto.ActivityLogResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The User Activity screen in one payload: login/logout history, recent activities,
 * modules touched, last activity time and any currently active session.
 */
@Schema(name = "UserActivitySummaryResponse")
public record UserActivitySummaryResponse(
        List<LoginHistoryResponse> loginHistory,
        List<ActivityLogResponse> recentActivities,
        List<String> modulesAccessed,
        Instant lastActivityAt,
        List<ActiveSessionResponse> activeSessions
) {
}
