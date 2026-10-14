package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "CommunicationDashboardResponse", description = "Communication statistics for a period")
public record CommunicationDashboardResponse(
        long totalSent,
        long totalDelivered,
        long totalFailed,
        long totalPending,
        Map<CommunicationChannel, ChannelStats> channels,
        @Schema(description = "TODAY, YESTERDAY, LAST_7_DAYS or LAST_30_DAYS") String period,
        Instant from,
        Instant to,
        @Schema(description = "Messages the recipient opened — needs the Meta webhook") long totalRead,
        @Schema(description = "delivered / sent, 1 decimal; null when nothing was sent") Double deliveryRatePercent,
        Double readRatePercent,
        List<DayPoint> daily,
        List<FailureRow> recentFailures,
        DailyReportStatus dailyReport,
        TemplateHealth templates,
        @Schema(description = "Newest delivery receipt recorded; null = the webhook has never reached us") Instant lastReceiptAt,
        @Schema(description = "Estimate from the company's own Meta rates — not a bill") WhatsAppCost whatsAppCost
) {
    @Schema(name = "CommunicationChannelStats")
    public record ChannelStats(long sent, long delivered, long failed, long pending, long cancelled) {
    }

    @Schema(name = "CommunicationWhatsAppCost")
    public record WhatsAppCost(String basis, boolean anyRateSet, boolean ratesComplete, List<CostLine> lines,
                               java.math.BigDecimal total, String currency) {
    }

    @Schema(name = "CommunicationCostLine")
    public record CostLine(String category, long messages, java.math.BigDecimal rate, java.math.BigDecimal amount) {
    }

    @Schema(name = "CommunicationDayPoint")
    public record DayPoint(LocalDate date, long sent, long delivered, long failed) {
    }

    @Schema(name = "CommunicationFailureRow")
    public record FailureRow(UUID id, UUID shipmentId, CommunicationEventType eventType, CommunicationChannel channel,
                             String recipient, String errorMessage, int attemptCount, Instant at) {
    }

    @Schema(name = "CommunicationDailyReportStatus")
    public record DailyReportStatus(boolean configured, int recipients, LocalDate lastDate, String lastStatus,
                                    Instant lastSentAt, Instant lastDeliveredAt, Instant lastReadAt, String lastError) {
    }

    @Schema(name = "CommunicationTemplateHealth")
    public record TemplateHealth(long approved, long pending, long rejected, long notSubmitted, long switchedOff, long total) {
    }
}
