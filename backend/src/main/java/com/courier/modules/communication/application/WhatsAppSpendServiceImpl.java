package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sums everything Meta billed from the 1st of this month (midnight India time) to now, using the
 * same pricing-analytics call the rate sync uses. A Meta failure is returned as a note, so the
 * dashboard around the card still loads.
 */
@Slf4j
@Service
public class WhatsAppSpendServiceImpl implements WhatsAppSpendService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal WARN_AT = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String READERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CommunicationSettingRepository settingRepository;
    private final WhatsAppProvider whatsAppProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public WhatsAppSpendServiceImpl(CommunicationSettingRepository settingRepository,
                                    WhatsAppProvider whatsAppProvider, ObjectMapper objectMapper) {
        this(settingRepository, whatsAppProvider, objectMapper, Clock.system(IST));
    }

    WhatsAppSpendServiceImpl(CommunicationSettingRepository settingRepository, WhatsAppProvider whatsAppProvider,
                             ObjectMapper objectMapper, Clock clock) {
        this.settingRepository = settingRepository;
        this.whatsAppProvider = whatsAppProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @PreAuthorize(READERS + " or hasAuthority('NOTIFICATION_READ')")
    public Spend current() {
        UUID companyId = CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
        Instant end = clock.instant();
        Instant start = ZonedDateTime.ofInstant(end, IST).withDayOfMonth(1).toLocalDate().atStartOfDay(IST).toInstant();

        CommunicationSetting setting = settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .orElse(null);
        if (setting == null) {
            return notConfigured(start, end);
        }
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.getConfigJson());
        WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());
        if (!credentials.hasBusinessAccount()) {
            return notConfigured(start, end);
        }

        BigDecimal budget = parseBudget(config.get("monthlyBudget"));
        WhatsAppProvider.PricingReport report;
        try {
            report = whatsAppProvider.fetchPricing(credentials, start, end);
        } catch (ProviderSendException e) {
            return new Spend(budget == null ? "NO_BUDGET" : "OK", null, null, budget, null, start, end, e.getMessage());
        }

        BigDecimal spent = report.points().stream().map(WhatsAppProvider.PricingPoint::cost)
                .filter(c -> c != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percent = budget == null ? null
                : spent.multiply(HUNDRED).divide(budget, 2, RoundingMode.HALF_UP);
        return new Spend(levelFor(spent, budget), report.currency(), spent, budget, percent, start, end,
                "As reported by Meta; recent messages can take a few hours to show.");
    }

    /** Package-visible for the unit tests: the thresholds that must not be wrong. */
    static String levelFor(BigDecimal spent, BigDecimal budget) {
        if (budget == null || budget.signum() <= 0) {
            return "NO_BUDGET";
        }
        BigDecimal percent = spent.multiply(HUNDRED).divide(budget, 6, RoundingMode.HALF_UP);
        if (percent.compareTo(HUNDRED) >= 0) {
            return "OVER";
        }
        return percent.compareTo(WARN_AT) >= 0 ? "WARN" : "OK";
    }

    private static BigDecimal parseBudget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Spend notConfigured(Instant start, Instant end) {
        return new Spend("NOT_CONFIGURED", null, null, null, null, start, end,
                "WhatsApp is not set up — add the Business Account ID and Access Token in Channel Settings.");
    }
}
