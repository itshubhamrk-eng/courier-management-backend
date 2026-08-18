package com.courier.modules.support.api;

import com.courier.modules.support.api.dto.NotificationResponse;
import com.courier.modules.support.application.NotificationService;
import com.courier.modules.support.domain.Notification;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The current user's own in-app notification feed — every row is scoped to the caller,
 *  there is no cross-user read here. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "The signed-in user's own in-app notification feed")
public class NotificationController {

    private final NotificationService service;

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getTicketId(), n.getFollowUpId(), n.isRead(), n.getCreatedAt());
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<Notification> page = service.list(pageable);
        return ApiResponse.success(PageResponse.from(page, NotificationController::toResponse));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success(service.unreadCount());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        service.markRead(id);
        return ApiResponse.success("Marked read");
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead();
        return ApiResponse.success("All marked read");
    }
}
