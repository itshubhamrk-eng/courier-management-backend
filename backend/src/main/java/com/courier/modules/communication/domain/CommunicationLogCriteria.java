package com.courier.modules.communication.domain;

import java.util.UUID;

public record CommunicationLogCriteria(
        UUID shipmentId,
        UUID customerId,
        CommunicationEventType eventType,
        CommunicationChannel channel,
        CommunicationStatus status
) {
    public static CommunicationLogCriteria empty() {
        return new CommunicationLogCriteria(null, null, null, null, null);
    }
}
