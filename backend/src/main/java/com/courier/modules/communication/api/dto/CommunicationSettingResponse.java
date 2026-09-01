package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Never carries the secret itself — only whether one is set, same rule {@code
 *  CompanyRazorpayConfigResponse} already follows for its own key secret. */
@Schema(name = "CommunicationSettingResponse")
public record CommunicationSettingResponse(
        UUID id,
        CommunicationChannel channel,
        boolean enabled,
        String provider,
        Map<String, String> config,
        @Schema(description = "Whether a secret has ever been saved — never the secret itself.")
        boolean secretConfigured,
        Instant updatedAt
) {
}
