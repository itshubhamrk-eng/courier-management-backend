package com.courier.modules.communication.application.command;

import com.courier.modules.communication.domain.TemplateStatus;

/** Full replacement of the editable fields. {@code eventType}/{@code channel} are
 *  immutable once created — changing either would just be a different template row. */
public record UpdateCommunicationTemplateCommand(
        String templateName,
        String subject,
        String content,
        TemplateStatus status,
        Long expectedVersion
) {
}
