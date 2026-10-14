package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendResult;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.DailyBusinessMetrics;
import com.courier.modules.communication.domain.DailyBusinessMetricsPort;
import com.courier.modules.communication.domain.DailyReportLog;
import com.courier.modules.communication.domain.DailyReportLogRepository;
import com.courier.shared.company.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportServiceImpl implements DailyReportService {

    static final int MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final CommunicationSettingRepository settingRepository;
    private final DailyReportLogRepository logRepository;
    private final DailyBusinessMetricsPort metricsPort;
    private final WhatsAppProvider whatsAppProvider;
    private final CommunicationTemplateService templateService;
    private final TemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    /** The zone a "business day" is cut in. One zone for the deployment, like the 09:00 trigger. */
    @Value("${app.communication.daily-report.zone:Asia/Kolkata}")
    private String zone = "Asia/Kolkata";

    @Override
    public int sendReportsFor(LocalDate reportDate) {
        int attempted = 0;
        for (CommunicationSetting setting : settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)) {
            UUID companyId = setting.getCompanyId();
            try {
                attempted += CompanyContext.runAs(companyId, () -> processCompany(companyId, setting, reportDate));
            } catch (RuntimeException e) {
                log.error("Daily report: unexpected failure for company {} on {}", companyId, reportDate, e);
            }
        }
        return attempted;
    }

    private int processCompany(UUID companyId, CommunicationSetting setting, LocalDate reportDate) {
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        Set<String> phones = parsePhones(config.get(CONFIG_PHONES));
        if (phones.isEmpty()) {
            return 0;
        }
        CommunicationTemplate template = templateService
                .findActive(companyId, CommunicationEventType.DAILY_BUSINESS_SUMMARY, CommunicationChannel.WHATSAPP)
                .orElse(null);
        if (template == null) {
            return 0; // the admin switched the daily summary template off
        }
        if (!template.isApprovedOrUnknownAtMeta()) {
            // Not an attempt: nothing to retry until Meta approves it, so don't burn the 3 tries.
            log.debug("Daily report: template {} is {} at Meta — skipping company {}", template.getTemplateName(),
                    template.getMetaStatus(), companyId);
            return 0;
        }

        DailyBusinessMetrics metrics = null;
        int attempted = 0;
        for (String phone : phones) {
            DailyReportLog row = claim(companyId, reportDate, phone);
            if (row == null) {
                continue;
            }
            if (metrics == null) {
                ZoneId z = ZoneId.of(zone);
                Instant from = reportDate.atStartOfDay(z).toInstant();
                Instant to = reportDate.plusDays(1).atStartOfDay(z).toInstant();
                metrics = metricsPort.metricsFor(companyId, reportDate, from, to);
            }
            send(row, setting, config, template, metrics);
            attempted++;
        }
        return attempted;
    }

    /** The existing row to retry, a fresh one (inserted now so a second node/tick sees it), or
     *  null when this recipient is already done for the day or out of attempts. */
    private DailyReportLog claim(UUID companyId, LocalDate reportDate, String phone) {
        DailyReportLog existing = logRepository
                .findByCompanyIdAndReportDateAndRecipient(companyId, reportDate, phone).orElse(null);
        if (existing != null) {
            return existing.isSent() || existing.getAttemptCount() >= MAX_ATTEMPTS ? null : existing;
        }
        DailyReportLog fresh = DailyReportLog.builder().reportDate(reportDate).recipient(phone).build();
        fresh.setCompanyId(companyId);
        try {
            return logRepository.saveAndFlush(fresh);
        } catch (DataIntegrityViolationException e) {
            return null; // another node inserted it between the lookup and here
        }
    }

    private void send(DailyReportLog row, CommunicationSetting setting, Map<String, String> config,
                      CommunicationTemplate template, DailyBusinessMetrics metrics) {
        try {
            WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                    config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());
            ProviderSendResult result = whatsAppProvider.send(WhatsAppPayloadBuilder.build(row.getRecipient(),
                    template, templateRenderer, variables(metrics), objectMapper), credentials);
            row.setStatus(CommunicationStatus.SENT);
            row.setProviderMessageId(result.providerMessageId());
            row.setErrorMessage(null);
            row.setSentAt(Instant.now());
        } catch (RuntimeException e) { // includes ProviderSendException
            row.setStatus(CommunicationStatus.FAILED);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            row.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
            log.warn("Daily report send failed: company={} day={} attempt={} — {}", row.getCompanyId(),
                    row.getReportDate(), row.getAttemptCount() + 1, message);
        }
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(Instant.now());
        logRepository.save(row);
    }

    /** The daily summary's {{variables}}. Which of them a template uses, and in what order, is the
     *  template's own business — {@link TemplateRenderer#positionalParameters} reads it off the content. */
    static Map<String, String> variables(DailyBusinessMetrics m) {
        Map<String, String> v = new java.util.LinkedHashMap<>();
        v.put("companyName", m.companyName() == null || m.companyName().isBlank() ? "your company" : m.companyName());
        v.put("reportDate", m.day().format(DAY));
        v.put("bookings", indian(BigDecimal.valueOf(m.bookings()), 0));
        v.put("revenue", indian(nz(m.revenue()), 2));
        v.put("rechargeAmount", indian(nz(m.rechargeAmount()), 2));
        v.put("rechargeCount", indian(BigDecimal.valueOf(m.rechargeCount()), 0));
        v.put("delivered", indian(BigDecimal.valueOf(m.delivered()), 0));
        v.put("cancelled", indian(BigDecimal.valueOf(m.cancelled()), 0));
        v.put("codToPaySettled", indian(nz(m.codToPaySettled()), 2));
        v.put("inTransit", indian(BigDecimal.valueOf(m.inTransitNow()), 0));
        v.put("outForDelivery", indian(BigDecimal.valueOf(m.outForDeliveryNow()), 0));
        return v;
    }

    /** Lakh/crore grouping ({@code 12,34,567.50}) — the JDK's own {@code en-IN} locale does not
     *  produce it, and the owner reading this on a phone expects it. */
    static String indian(BigDecimal value, int scale) {
        BigDecimal v = value.setScale(scale, java.math.RoundingMode.HALF_UP);
        String plain = v.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        for (int i = 0; i < len; i++) {
            int fromRight = len - i;
            grouped.append(whole.charAt(i));
            if (fromRight > 1 && (fromRight == 4 || (fromRight > 4 && fromRight % 2 == 0))) {
                grouped.append(',');
            }
        }
        return (v.signum() < 0 ? "-" : "") + grouped + fraction;
    }

    /** Digits only, de-duplicated, order kept — what the user typed minus spaces/+/dashes. */
    static Set<String> parsePhones(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[,;\\n]")) {
            String digits = part.replaceAll("\\D", "");
            if (digits.length() >= 10) {
                out.add(digits);
            }
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
