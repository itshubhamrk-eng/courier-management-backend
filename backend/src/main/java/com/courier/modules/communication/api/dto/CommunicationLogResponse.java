package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "CommunicationLogResponse")
public record CommunicationLogResponse(
        UUID id,
        UUID shipmentId,
        UUID customerId,
        CommunicationEventType eventType,
        CommunicationChannel channel,
        String recipient,
        UUID templateId,
        CommunicationStatus status,
        String providerMessageId,
        String errorMessage,
        int attemptCount,
        Instant lastAttemptAt,
        Instant nextRetryAt,
        Instant sentAt,
        Instant createdAt
) {
}
