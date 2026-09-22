package com.courier.modules.auth.api.dto;

import com.courier.modules.auth.domain.LoginFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "LoginHistoryResponse")
public record LoginHistoryResponse(
        UUID id,
        UUID userId,
        String attemptedEmail,
        String eventType,
        boolean success,
        LoginFailureReason failureReason,
        UUID sessionId,
        String ipAddress,
        String device,
        String browser,
        String os,
        Instant occurredAt,
        Instant logoutAt
) {
}
