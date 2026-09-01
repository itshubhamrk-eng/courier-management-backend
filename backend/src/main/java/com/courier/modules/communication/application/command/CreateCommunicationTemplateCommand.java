package com.courier.modules.communication.application.command;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;

public record CreateCommunicationTemplateCommand(
        CommunicationEventType eventType,
        CommunicationChannel channel,
        String templateName,
        String subject,
        String content
) {
}
