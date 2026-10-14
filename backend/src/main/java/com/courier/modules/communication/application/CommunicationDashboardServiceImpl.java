package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.modules.communication.domain.DailyReportLog;
import com.courier.modules.communication.domain.DailyReportLogRepository;
import com.courier.modules.communication.domain.TemplateStatus;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Communication statistics for a chosen window. {@code CommunicationLog.createdAt} decides which
 * window a message belongs to, cut at midnight in the reporting zone (IST by default — the same zone
 * the 9:00 AM summary uses), not UTC. {@code Sent} folds in {@code DELIVERED} (a delivered message
 * was necessarily sent first), so Delivered is always a subset of Sent. {@code DELIVERED} and the
 * Read count are real only once Meta's webhook is connected — until then they stay 0, and
 * {@code lastReceiptAt} (null) is how the UI knows to say so.
 */
@Service
@RequiredArgsConstructor
public class CommunicationDashboardServiceImpl implements CommunicationDashboardService {

    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";

    private final CommunicationLogRepository repository;
    private final DailyReportLogRepository dailyReportLogRepository;
    private final CommunicationSettingRepository settingRepository;
    private final CommunicationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    /** One zone for the deployment, shared with the daily summary. */
    @Value("${app.communication.daily-report.zone:Asia/Kolkata}")
    private String zone = "Asia/Kolkata";

    @Override
    public CommunicationDashboardSummary today() {
        return summary(Period.TODAY);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public CommunicationDashboardSummary summary(Period period) {
        UUID companyId = requireCompany();
        ZoneId z = ZoneId.of(zone);
        LocalDate today = LocalDate.now(z);
        LocalDate firstDay = switch (period) {
            case TODAY -> today;
            case YESTERDAY -> today.minusDays(1);
            case LAST_7_DAYS -> today.minusDays(6);
            case LAST_30_DAYS -> today.minusDays(29);
        };
        LocalDate lastDay = period == Period.YESTERDAY ? today.minusDays(1) : today;
        Instant from = firstDay.atStartOfDay(z).toInstant();
        Instant to = lastDay.plusDays(1).atStartOfDay(z).toInstant();

        Map<CommunicationChannel, ChannelSummary> channels = new EnumMap<>(CommunicationChannel.class);
        for (CommunicationChannel channel : CommunicationChannel.values()) {
            channels.put(channel, new ChannelSummary(0, 0, 0, 0, 0));
        }

        long totalSent = 0, totalDelivered = 0, totalFailed = 0, totalPending = 0;
        for (var row : repository.countByChannelAndStatusBetween(companyId, from, to)) {
            ChannelSummary current = channels.get(row.getChannel());
            long sent = current.sent(), delivered = current.delivered(), failed = current.failed(),
                    pending = current.pending(), cancelled = current.cancelled();
            long count = row.getTotal();
            switch (row.getStatus()) {
                case SENT -> {
                    sent += count;
                    totalSent += count;
                }
                case DELIVERED -> {
                    sent += count;
                    delivered += count;
                    totalSent += count;
                    totalDelivered += count;
                }
                case FAILED -> {
                    failed += count;
                    totalFailed += count;
                }
                case PENDING -> {
                    pending += count;
                    totalPending += count;
                }
                case CANCELLED -> cancelled += count;
                default -> {
                }
            }
            channels.put(row.getChannel(), new ChannelSummary(sent, delivered, failed, pending, cancelled));
        }

        Instant lastReceiptAt = repository.findLastDeliveredAt(companyId);
        long totalRead = repository.countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndReadAtNotNull(
                companyId, from, to);

        return new CommunicationDashboardSummary(totalSent, totalDelivered, totalFailed, totalPending, channels,
                period, from, to, totalRead,
                percent(totalDelivered, totalSent), percent(totalRead, totalSent),
                daily(companyId, firstDay, lastDay, from, to, z), failures(companyId, from, to),
                dailyReport(companyId), templateHealth(companyId), lastReceiptAt, whatsAppCost(companyId, from, to, lastReceiptAt != null));
    }

    /** One zero-filled point per calendar day, so the chart never skips a quiet day. */
    private List<DayPoint> daily(UUID companyId, LocalDate firstDay, LocalDate lastDay, Instant from, Instant to, ZoneId z) {
        Map<LocalDate, long[]> buckets = new TreeMap<>();
        for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
            buckets.put(d, new long[3]);
        }
        for (var row : repository.findSeriesRows(companyId, from, to)) {
            long[] b = buckets.get(row.getAt().atZone(z).toLocalDate());
            if (b == null) {
                continue;
            }
            switch (row.getStatus()) {
                case SENT -> b[0]++;
                case DELIVERED -> {
                    b[0]++;
                    b[1]++;
                }
                case FAILED -> b[2]++;
                default -> {
                }
            }
        }
        List<DayPoint> out = new ArrayList<>();
        buckets.forEach((d, b) -> out.add(new DayPoint(d, b[0], b[1], b[2])));
        return out;
    }

    private List<FailureRow> failures(UUID companyId, Instant from, Instant to) {
        List<FailureRow> out = new ArrayList<>();
        for (CommunicationLog l : repository
                .findTop8ByCompanyIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByUpdatedAtDesc(
                        companyId, CommunicationStatus.FAILED, from, to)) {
            out.add(new FailureRow(l.getId(), l.getShipmentId(), l.getEventType(), l.getChannel(), l.getRecipient(),
                    l.getErrorMessage(), l.getAttemptCount(),
                    l.getLastAttemptAt() != null ? l.getLastAttemptAt() : l.getUpdatedAt()));
        }
        return out;
    }

    private DailyReportStatus dailyReport(UUID companyId) {
        int recipients = settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .filter(CommunicationSetting::isEnabled)
                .map(s -> DailyReportServiceImpl.parsePhones(
                        CommunicationConfigJson.read(objectMapper, s.getConfigJson()).get(DailyReportService.CONFIG_PHONES)).size())
                .orElse(0);
        DailyReportLog last = dailyReportLogRepository.findFirstByCompanyIdOrderByReportDateDescCreatedAtDesc(companyId)
                .orElse(null);
        if (last == null) {
            return new DailyReportStatus(recipients > 0, recipients, null, null, null, null, null, null);
        }
        return new DailyReportStatus(recipients > 0, recipients, last.getReportDate(), last.getStatus().name(),
                last.getSentAt(), last.getDeliveredAt(), last.getReadAt(), last.getErrorMessage());
    }

    private TemplateHealth templateHealth(UUID companyId) {
        long approved = 0, pending = 0, rejected = 0, notSubmitted = 0, off = 0, total = 0;
        for (CommunicationTemplate t : templateRepository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(companyId)) {
            total++;
            if (t.getStatus() == TemplateStatus.INACTIVE) {
                off++;
            }
            if (t.getChannel() != CommunicationChannel.WHATSAPP) {
                continue;
            }
            String meta = t.getMetaStatus();
            if (meta == null) {
                notSubmitted++;
            } else if ("APPROVED".equals(meta)) {
                approved++;
            } else if ("PENDING".equals(meta) || "IN_APPEAL".equals(meta)) {
                pending++;
            } else {
                rejected++; // REJECTED, PAUSED, DISABLED — none of them can send
            }
        }
        return new TemplateHealth(approved, pending, rejected, notSubmitted, off, total);
    }

    private static final List<String> CATEGORY_ORDER = List.of("UTILITY", "MARKETING", "AUTHENTICATION");

    /**
     * Messages by template category x the company's own rate. Meta bills a template message when it
     * is <em>delivered</em>, so once receipts are live only DELIVERED rows count; before that every
     * SENT row is counted and the figure is an upper bound. The daily 9 AM summary is a WhatsApp
     * message too and is added under its template's category.
     */
    private WhatsAppCost whatsAppCost(UUID companyId, Instant from, Instant to, boolean receiptsLive) {
        List<CommunicationStatus> billable = receiptsLive
                ? List.of(CommunicationStatus.DELIVERED)
                : List.of(CommunicationStatus.SENT, CommunicationStatus.DELIVERED);

        Map<String, Long> byCategory = new TreeMap<>();
        for (var row : repository.countWhatsAppByCategory(companyId, billable, from, to)) {
            byCategory.merge(row.getCategory() == null ? "UTILITY" : row.getCategory(), row.getTotal(), Long::sum);
        }
        long dailySent = dailyReportLogRepository
                .countByCompanyIdAndStatusInAndSentAtGreaterThanEqualAndSentAtLessThan(companyId, billable, from, to);
        if (dailySent > 0) {
            String category = templateRepository.findByCompanyIdAndEventTypeAndChannel(companyId,
                            com.courier.modules.communication.domain.CommunicationEventType.DAILY_BUSINESS_SUMMARY,
                            CommunicationChannel.WHATSAPP)
                    .map(CommunicationTemplate::getCategory).orElse("UTILITY");
            byCategory.merge(category, dailySent, Long::sum);
        }

        Map<String, String> config = settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .map(s -> CommunicationConfigJson.read(objectMapper, s.getConfigJson())).orElse(Map.of());
        Map<String, java.math.BigDecimal> rates = new java.util.HashMap<>();
        rates.put("UTILITY", rate(config.get("rateUtility")));
        rates.put("MARKETING", rate(config.get("rateMarketing")));
        rates.put("AUTHENTICATION", rate(config.get("rateAuthentication")));

        List<CostLine> lines = new ArrayList<>();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        boolean complete = true;
        for (String category : CATEGORY_ORDER) {
            long messages = byCategory.getOrDefault(category, 0L);
            if (messages == 0) {
                continue;
            }
            java.math.BigDecimal rate = rates.get(category);
            java.math.BigDecimal amount = rate == null ? null
                    : rate.multiply(java.math.BigDecimal.valueOf(messages)).setScale(2, java.math.RoundingMode.HALF_UP);
            if (amount == null) {
                complete = false;
            } else {
                total = total.add(amount);
            }
            lines.add(new CostLine(category, messages, rate, amount));
        }
        boolean anyRate = rates.values().stream().anyMatch(java.util.Objects::nonNull);
        String currency = config.getOrDefault("rateCurrency", "");
        return new WhatsAppCost(receiptsLive ? "DELIVERED" : "SENT", anyRate, complete, lines, total,
                currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase());
    }

    /** A rate the admin typed: a non-negative number, else "not set" (never a guess, never an error). */
    static java.math.BigDecimal rate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw.trim().replace(",", ""));
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One decimal place; null (not 0%) when there is nothing to divide by. */
    static Double percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. The communication dashboard belongs to a company."));
    }
}
