package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Query parameters of {@code GET /api/v1/communication/logs}, bound as a parameter object. */
@Schema(name = "CommunicationLogSearchRequest", description = "Communication log search filters")
public record CommunicationLogSearchRequest(
        UUID shipmentId,
        UUID customerId,
        CommunicationEventType eventType,
        CommunicationChannel channel,
        CommunicationStatus status
) {
    public static CommunicationLogSearchRequest empty() {
        return new CommunicationLogSearchRequest(null, null, null, null, null);
    }
}
