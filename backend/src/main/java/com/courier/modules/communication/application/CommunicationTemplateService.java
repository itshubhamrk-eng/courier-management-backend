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

    /** What a new template for this event + channel would start as (name, subject, text). */
    StarterTemplate starter(com.courier.modules.communication.domain.CommunicationEventType eventType,
                            com.courier.modules.communication.domain.CommunicationChannel channel);

    /**
     * Puts the template back to the app's own default for its event + channel: name, subject and
     * text, UTILITY / en_US, no header, footer, buttons or custom samples, and any Meta approval
     * dropped (it described the old text). Whether it sends (ACTIVE/INACTIVE) is left as it was.
     */
    CommunicationTemplate resetToDefault(UUID id);

    record StarterTemplate(String templateName, String subject, String content, boolean seeded) {
    }

    /** Switches one template's sending on (ACTIVE) or off (INACTIVE) and nothing else — no other
     *  field is read or rewritten, so it can never clear a Meta approval or race an edit in progress.
     *  Takes effect on the next message: the send path only uses ACTIVE templates. */
    CommunicationTemplate setStatus(UUID id, com.courier.modules.communication.domain.TemplateStatus status);

    /** No {@code @PreAuthorize} — called from {@code CommunicationOrchestrator} on the
     *  {@code AFTER_COMMIT} listener thread, which carries no authenticated caller. Never
     *  exposed through a controller directly. */
    Optional<CommunicationTemplate> findActive(UUID companyId, CommunicationEventType eventType,
                                                CommunicationChannel channel);

    record RenderedPreview(String subject, String content) {
    }
}
