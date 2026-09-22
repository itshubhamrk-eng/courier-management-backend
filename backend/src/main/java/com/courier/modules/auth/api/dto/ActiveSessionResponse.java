package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ActiveSessionResponse")
public record ActiveSessionResponse(
        UUID id,
        String deviceName,
        String deviceType,
        String browser,
        String os,
        String ipAddress,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean rememberMe
) {
}
