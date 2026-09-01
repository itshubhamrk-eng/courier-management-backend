package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateCommunicationTemplateRequest")
public record UpdateCommunicationTemplateRequest(
        @NotBlank @Size(max = 150) String templateName,
        @Size(max = 255) String subject,
        @NotBlank String content,
        @NotNull TemplateStatus status,
        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
