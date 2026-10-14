package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationTemplate;

import java.util.UUID;

/**
 * Files a WhatsApp template with Meta and reads back where it stands, using the company's own
 * WhatsApp Business Account ID and access token from Channel Settings. Meta still reviews the
 * template — approval is theirs, not ours.
 */
public interface WhatsAppTemplateSubmissionService {

    String DEFAULT_CATEGORY = "UTILITY";
    String DEFAULT_LANGUAGE = "en_US";

    /**
     * @param category UTILITY (default), MARKETING or AUTHENTICATION
     * @param language Meta language code, e.g. {@code en_US} (default) — must match the language a
     *                 send uses ({@code app.communication.whatsapp.template-language})
     */
    CommunicationTemplate submit(UUID templateId, String category, String language);

    /** Reads Meta's current status for this template and stores it. */
    CommunicationTemplate refreshStatus(UUID templateId);
}
