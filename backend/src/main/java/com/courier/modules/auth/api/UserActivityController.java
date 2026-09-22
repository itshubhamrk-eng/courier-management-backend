package com.courier.modules.auth.api;

import com.courier.modules.auth.api.dto.UserActivitySummaryResponse;
import com.courier.modules.auth.application.UserActivityService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The User Activity screen — an admin picks a user and sees their login history, recent
 * activities, modules accessed, last activity and any active session in one call.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/activity")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Activity", description = "One user's login history, recent activity and active sessions")
public class UserActivityController {

    private final UserActivityService service;

    @GetMapping
    @Operation(summary = "One user's activity summary",
            description = "Login history, logout history, recent activities, modules accessed, "
                    + "last activity and any currently active session.")
    public ApiResponse<UserActivitySummaryResponse> summary(@PathVariable UUID userId) {
        return ApiResponse.success(service.summaryFor(userId));
    }
}
