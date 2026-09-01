package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateCommunicationTemplateRequest")
public record CreateCommunicationTemplateRequest(
        @NotNull CommunicationEventType eventType,
        @NotNull CommunicationChannel channel,
        @NotBlank @Size(max = 150) String templateName,
        @Size(max = 255) @Schema(description = "Email only") String subject,
        @NotBlank String content
) {
}
