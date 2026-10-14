package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rate per category = Meta's billed cost / messages delivered, over the last {@value #LOOKBACK_DAYS}
 * days. Only {@code REGULAR} pricing counts: free customer-service and entry-point messages carry no
 * cost and would drag the average to a price nobody is actually charged. {@code MARKETING_LITE} is
 * folded into marketing; {@code SERVICE} and referral conversions are free and ignored; international
 * authentication is priced differently and left out rather than blended into the domestic rate.
 *
 * <p>Writes through {@link CommunicationSettingService#upsert} like any other settings change, so
 * it is audited and role-checked, and the stored access token is untouched (a blank secret keeps it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppRateSyncServiceImpl implements WhatsAppRateSyncService {

    private static final String WRITERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final Map<String, String> CATEGORY_TO_KEY = Map.of(
            "UTILITY", "rateUtility", "MARKETING", "rateMarketing", "AUTHENTICATION", "rateAuthentication");

    private final CommunicationSettingRepository settingRepository;
    private final CommunicationSettingService settingService;
    private final WhatsAppProvider whatsAppProvider;
    private final ObjectMapper objectMapper;

    @Override
    @PreAuthorize(WRITERS)
    public FetchedRates fetchAndSave() {
        UUID companyId = CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
        CommunicationSetting setting = settingRepository.findByCompanyIdAndChannel(companyId, CommunicationChannel.WHATSAPP)
                .orElseThrow(() -> new BusinessRuleException("WhatsApp is not configured — open Channel Settings and "
                        + "enter the Business Account ID and Access Token first."));
        Map<String, String> config = new LinkedHashMap<>(CommunicationConfigJson.read(objectMapper, setting.getConfigJson()));
        WhatsAppProvider.WhatsAppCredentials credentials = new WhatsAppProvider.WhatsAppCredentials(
                config.get("phoneNumberId"), config.get("businessAccountId"), setting.getSecret());
        if (!credentials.hasBusinessAccount()) {
            throw new BusinessRuleException("The WhatsApp Business Account ID and Access Token are required to read "
                    + "Meta's billing — set them in Channel Settings.");
        }

        Instant to = Instant.now();
        WhatsAppProvider.PricingReport report;
        try {
            report = whatsAppProvider.fetchPricing(credentials, to.minus(LOOKBACK_DAYS, ChronoUnit.DAYS), to);
        } catch (ProviderSendException e) {
            throw new BusinessRuleException(e.getMessage()); // Meta's own reason, e.g. a missing permission
        }

        List<Derived> derived = derive(report.points());
        if (derived.isEmpty()) {
            return new FetchedRates(report.currency(), LOOKBACK_DAYS, List.of(), List.of(), BigDecimal.ZERO,
                    "Meta has not billed any messages in the last " + LOOKBACK_DAYS + " days, so there is nothing to "
                            + "work a rate out from yet. Messages from Meta's test number are free. Enter the rates by "
                            + "hand, or fetch again after real messages have been delivered.");
        }

        List<String> saved = new ArrayList<>();
        BigDecimal billed = BigDecimal.ZERO;
        for (Derived d : derived) {
            config.put(CATEGORY_TO_KEY.get(d.category()), d.rate().stripTrailingZeros().toPlainString());
            saved.add(d.category());
            billed = billed.add(d.cost());
        }
        if (report.currency() != null && !report.currency().isBlank()) {
            config.put("rateCurrency", report.currency());
        }
        settingService.upsert(new UpsertCommunicationSettingCommand(CommunicationChannel.WHATSAPP, setting.isEnabled(),
                setting.getProvider(), config, null));
        log.info("WhatsApp rates fetched from Meta for company {}: {}", companyId, saved);

        return new FetchedRates(report.currency(), LOOKBACK_DAYS, derived, saved, billed,
                "Rates are Meta's billed cost divided by the messages it delivered over the last " + LOOKBACK_DAYS
                        + " days" + (report.currency() == null ? "." : ", in " + report.currency() + "."));
    }

    /** Package-visible for the unit tests: the arithmetic that must not be wrong. */
    static List<Derived> derive(List<WhatsAppProvider.PricingPoint> points) {
        Map<String, long[]> volume = new LinkedHashMap<>();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        for (WhatsAppProvider.PricingPoint p : points) {
            if (!"REGULAR".equals(p.pricingType()) || p.category() == null) {
                continue;
            }
            String category = switch (p.category()) {
                case "UTILITY" -> "UTILITY";
                case "MARKETING", "MARKETING_LITE" -> "MARKETING";
                case "AUTHENTICATION" -> "AUTHENTICATION";
                default -> null;
            };
            if (category == null) {
                continue;
            }
            volume.computeIfAbsent(category, k -> new long[1])[0] += p.volume();
            cost.merge(category, p.cost() == null ? BigDecimal.ZERO : p.cost(), BigDecimal::add);
        }
        List<Derived> out = new ArrayList<>();
        for (String category : List.of("UTILITY", "MARKETING", "AUTHENTICATION")) {
            long v = volume.getOrDefault(category, new long[1])[0];
            BigDecimal c = cost.getOrDefault(category, BigDecimal.ZERO);
            if (v > 0 && c.signum() > 0) {
                out.add(new Derived(category, v, c, c.divide(BigDecimal.valueOf(v), 4, RoundingMode.HALF_UP)));
            }
        }
        return out;
    }
}
