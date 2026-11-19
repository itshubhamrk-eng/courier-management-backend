package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationSettingService {

    /** Get-or-seed all three channel rows (WhatsApp/SMS/Email) for the caller's company. */
    List<CommunicationSetting> list();

    CommunicationSetting get(CommunicationChannel channel);

    CommunicationSetting upsert(UpsertCommunicationSettingCommand command);

    /** Not a live network probe (this project's own honesty rule: don't fabricate a
     *  handshake no real vendor account can actually confirm in this dev environment) —
     *  validates the configured fields are complete enough to attempt a send. */
    ConnectionTestResult testConnection(CommunicationChannel channel);

    /** Live send of an approved WhatsApp template to {@code recipient} with this company's stored
     *  credentials — the only real end-to-end check of them. {@code templateName} blank picks an
     *  APPROVED template of this app (sample values fill its variables); a name not known to the app
     *  is sent as a template with no variables in {@code language} (default en_US). Meta's
     *  {@code hello_world} is no use: Meta only allows it from its public test numbers. */
    ConnectionTestResult sendTestMessage(String recipient, String templateName, String language);

    /** No {@code @PreAuthorize} — called from {@code CommunicationOrchestrator}/{@code
     *  CommunicationSendService} on the {@code AFTER_COMMIT} listener thread and the
     *  scheduler thread, neither of which carries an authenticated caller. Never exposed
     *  through a controller. */
    Optional<CommunicationSetting> findEnabled(UUID companyId, CommunicationChannel channel);

    /** Cheap existence check (no entity hydration, so {@code secretEncrypted}'s
     *  {@code EncryptedStringConverter} never runs) — lets {@code CommunicationOrchestrator}
     *  skip its whole per-channel loop for the common case of a company that has never
     *  configured Communication Center at all, rather than paying 3 channel lookups + 3
     *  CANCELLED-row inserts per shipment event for a feature it never opted into. */
    boolean hasAnyEnabled(UUID companyId);

    record ConnectionTestResult(boolean ok, String message) {
    }
}
