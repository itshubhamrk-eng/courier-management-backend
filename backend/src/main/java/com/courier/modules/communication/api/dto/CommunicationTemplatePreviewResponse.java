package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CommunicationTemplatePreviewResponse",
        description = "The template rendered against synthetic sample shipment data")
public record CommunicationTemplatePreviewResponse(String subject, String content) {
}
