package com.courier.modules.communication.application.command;

import com.courier.modules.communication.domain.TemplateButton;
import com.courier.modules.communication.domain.TemplateStatus;

import java.util.List;
import java.util.Map;

/** Full replacement of the editable fields. {@code eventType}/{@code channel} are
 *  immutable once created — changing either would just be a different template row.
 *
 *  <p>The WhatsApp parts ({@code category} .. {@code variableSamples}) are tri-state: {@code null}
 *  leaves the stored value alone, blank / empty clears it, anything else replaces it. */
public record UpdateCommunicationTemplateCommand(
        String templateName,
        String subject,
        String content,
        TemplateStatus status,
        Long expectedVersion,
        String category,
        String language,
        String headerText,
        String footerText,
        List<TemplateButton> buttons,
        Map<String, String> variableSamples
) {
    /** The original five-field shape — none of the WhatsApp parts is touched. */
    public UpdateCommunicationTemplateCommand(String templateName, String subject, String content,
                                              TemplateStatus status, Long expectedVersion) {
        this(templateName, subject, content, status, expectedVersion, null, null, null, null, null, null);
    }
}
