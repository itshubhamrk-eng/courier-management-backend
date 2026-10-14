package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TemplateStarterResponse", description = "What a new template for an event + channel starts as")
public record TemplateStarterResponse(
        String templateName,
        String subject,
        String content,
        @Schema(description = "true = the app's own seeded default; false = a generated starter") boolean seeded
) {
}
