package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommunicationDashboardService {

    /** The window the dashboard reports on, cut at midnight in the reporting zone (IST by default),
     *  not UTC — a courier's "today" starts at 12:00 AM India time. */
    enum Period {
        TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS
    }

    CommunicationDashboardSummary today();

    CommunicationDashboardSummary summary(Period period);

    /**
     * @param totalRead            messages in the window the recipient has opened (needs the Meta webhook)
     * @param deliveryRatePercent  delivered / sent, one decimal; null when nothing was sent
     * @param readRatePercent      read / sent, one decimal; null when nothing was sent
     * @param daily                one point per calendar day of the window, zero-filled
     * @param lastReceiptAt        newest delivery receipt ever recorded; null = webhook never reached us
     */
    record CommunicationDashboardSummary(
            long totalSent, long totalDelivered, long totalFailed, long totalPending,
            Map<CommunicationChannel, ChannelSummary> channels,
            Period period, Instant from, Instant to,
            long totalRead, Double deliveryRatePercent, Double readRatePercent,
            List<DayPoint> daily, List<FailureRow> recentFailures,
            DailyReportStatus dailyReport, TemplateHealth templates, Instant lastReceiptAt,
            WhatsAppCost whatsAppCost
    ) {
        /** Counts and channels only — the original shape, everything else empty. */
        public CommunicationDashboardSummary(long totalSent, long totalDelivered, long totalFailed, long totalPending,
                                             Map<CommunicationChannel, ChannelSummary> channels) {
            this(totalSent, totalDelivered, totalFailed, totalPending, channels, Period.TODAY, null, null,
                    0, null, null, List.of(), List.of(), null, null, null, null);
        }
    }

    record ChannelSummary(long sent, long delivered, long failed, long pending, long cancelled) {
    }

    /**
     * An estimate of what Meta will bill for WhatsApp in the window — never a bill (Meta's own
     * Insights page is). Rates are the company's own, entered in Channel Settings, because Meta's
     * prices change by country and date and are not ours to guess.
     *
     * @param basis         DELIVERED once the webhook is live (Meta bills delivered messages), else SENT
     *                      (an upper bound: every message that left is assumed delivered)
     * @param anyRateSet    at least one rate has been entered
     * @param currency      the currency the company's rates are in (Channel Settings "rateCurrency", INR by default)
     * @param ratesComplete every category that had messages has a rate, so {@code total} is the whole estimate
     */
    record WhatsAppCost(String basis, boolean anyRateSet, boolean ratesComplete, List<CostLine> lines,
                        java.math.BigDecimal total, String currency) {
    }

    /** @param rate null when no rate was entered for this category; {@code amount} is then null too */
    record CostLine(String category, long messages, java.math.BigDecimal rate, java.math.BigDecimal amount) {
    }

    record DayPoint(LocalDate date, long sent, long delivered, long failed) {
    }

    record FailureRow(UUID id, UUID shipmentId, CommunicationEventType eventType, CommunicationChannel channel,
                      String recipient, String errorMessage, int attemptCount, Instant at) {
    }

    /** @param lastStatus SENT / DELIVERED / FAILED / PENDING, or null when no summary has ever gone out */
    record DailyReportStatus(boolean configured, int recipients, LocalDate lastDate, String lastStatus,
                             Instant lastSentAt, Instant lastDeliveredAt, Instant lastReadAt, String lastError) {
    }

    /** WhatsApp templates by where Meta stands, plus every template (any channel) switched off. */
    record TemplateHealth(long approved, long pending, long rejected, long notSubmitted, long switchedOff, long total) {
    }
}
