package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.CreateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpdateCommunicationTemplateCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationTemplateService {

    /** Get-or-seed the four default events x three channels for the caller's company. */
    List<CommunicationTemplate> list();

    CommunicationTemplate getById(UUID id);

    CommunicationTemplate create(CreateCommunicationTemplateCommand command);

    CommunicationTemplate update(UUID id, UpdateCommunicationTemplateCommand command);

    /** Renders the template against synthetic sample data — no real shipment required. */
    RenderedPreview preview(UUID id);

    /** No {@code @PreAuthorize} — called from {@code CommunicationOrchestrator} on the
     *  {@code AFTER_COMMIT} listener thread, which carries no authenticated caller. Never
     *  exposed through a controller directly. */
    Optional<CommunicationTemplate> findActive(UUID companyId, CommunicationEventType eventType,
                                                CommunicationChannel channel);

    record RenderedPreview(String subject, String content) {
    }
}
