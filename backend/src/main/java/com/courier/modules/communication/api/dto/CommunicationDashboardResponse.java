package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "CommunicationDashboardResponse", description = "Today's communication statistics")
public record CommunicationDashboardResponse(
        long totalSent,
        long totalDelivered,
        long totalFailed,
        long totalPending,
        Map<CommunicationChannel, ChannelStats> channels
) {
    @Schema(name = "CommunicationChannelStats")
    public record ChannelStats(long sent, long delivered, long failed, long pending, long cancelled) {
    }
}
