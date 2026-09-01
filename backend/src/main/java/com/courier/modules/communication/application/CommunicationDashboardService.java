package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;

import java.util.Map;

public interface CommunicationDashboardService {

    CommunicationDashboardSummary today();

    record CommunicationDashboardSummary(
            long totalSent, long totalDelivered, long totalFailed, long totalPending,
            Map<CommunicationChannel, ChannelSummary> channels
    ) {
    }

    record ChannelSummary(long sent, long delivered, long failed, long pending, long cancelled) {
    }
}
