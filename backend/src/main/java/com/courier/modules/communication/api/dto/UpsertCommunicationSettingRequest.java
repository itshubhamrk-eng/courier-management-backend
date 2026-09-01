package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Schema(name = "UpsertCommunicationSettingRequest")
public record UpsertCommunicationSettingRequest(
        boolean enabled,
        @Size(max = 50) String provider,
        @Schema(description = "WhatsApp: phoneNumberId, businessAccountId. SMS: apiUrl, senderId. "
                + "Email: fromName, fromEmail.")
        Map<String, String> config,
        @Size(max = 2000)
        @Schema(description = "Access token (WhatsApp) / API key (SMS). Blank or omitted keeps the "
                + "one already stored. Not used for Email.")
        String secret
) {
}
