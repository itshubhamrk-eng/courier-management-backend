package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, NotificationType type, String title, String message,
        UUID ticketId, boolean read, Instant createdAt) {
}
