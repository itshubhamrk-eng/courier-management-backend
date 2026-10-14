package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.TemplateButton;
import com.courier.modules.communication.domain.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "CommunicationTemplateResponse")
public record CommunicationTemplateResponse(
        UUID id,
        CommunicationEventType eventType,
        CommunicationChannel channel,
        String templateName,
        String subject,
        String content,
        TemplateStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        String metaTemplateId,
        String metaStatus,
        String metaStatusDetail,
        String metaCategory,
        String metaLanguage,
        Instant metaSubmittedAt,
        String category,
        String language,
        String headerText,
        String footerText,
        List<TemplateButton> buttons,
        Map<String, String> variableSamples
) {
}
