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

    /** No {@code @PreAuthorize} — called from {@code CommunicationOrchestrator}/{@code
     *  CommunicationSendService} on the {@code AFTER_COMMIT} listener thread and the
     *  scheduler thread, neither of which carries an authenticated caller. Never exposed
     *  through a controller. */
    Optional<CommunicationSetting> findEnabled(UUID companyId, CommunicationChannel channel);

    record ConnectionTestResult(boolean ok, String message) {
    }
}
