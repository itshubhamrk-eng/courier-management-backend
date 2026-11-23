package com.courier.modules.communication.application;

import com.courier.modules.communication.application.WhatsAppSpendService.Spend;
import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.application.provider.WhatsAppProvider.PricingPoint;
import com.courier.modules.communication.application.provider.WhatsAppProvider.PricingReport;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.company.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppSpendServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // 2026-11-23 10:00 IST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T04:30:00Z"), IST);

    @Mock CommunicationSettingRepository settingRepository;
    @Mock WhatsAppProvider provider;

    private WhatsAppSpendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppSpendServiceImpl(settingRepository, provider, new ObjectMapper(), CLOCK);
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private void configure(String json, String secret) {
        CommunicationSetting setting = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP).enabled(true)
                .provider("META_CLOUD_API").configJson(json).build();
        setting.setSecret(secret);
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP)).thenReturn(Optional.of(setting));
    }

    private static PricingPoint pt(String cost) {
        return new PricingPoint("UTILITY", "REGULAR", 10, cost == null ? null : new BigDecimal(cost));
    }

    @Test
    void levelForSitsOnTheThresholds() {
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("79.99"), new BigDecimal("100"))).isEqualTo("OK");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("80"), new BigDecimal("100"))).isEqualTo("WARN");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("99.99"), new BigDecimal("100"))).isEqualTo("WARN");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("100"), new BigDecimal("100"))).isEqualTo("OVER");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("150"), new BigDecimal("100"))).isEqualTo("OVER");
    }

    @Test
    void levelForWithoutAUsableBudgetIsNoBudget() {
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("50"), null)).isEqualTo("NO_BUDGET");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("50"), BigDecimal.ZERO)).isEqualTo("NO_BUDGET");
        assertThat(WhatsAppSpendServiceImpl.levelFor(new BigDecimal("50"), new BigDecimal("-5"))).isEqualTo("NO_BUDGET");
    }

    @Test
    void currentSumsEveryPointAndTreatsNullCostAsZero() {
        configure("{\"businessAccountId\":\"W1\",\"monthlyBudget\":\"100\"}", "tok");
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new PricingReport("INR", List.of(pt("40"), pt(null), pt("25.5"))));

        Spend s = service.current();

        assertThat(s.spent()).isEqualByComparingTo("65.5");
        assertThat(s.budget()).isEqualByComparingTo("100");
        assertThat(s.percentUsed()).isEqualByComparingTo("65.50");
        assertThat(s.level()).isEqualTo("OK");
        assertThat(s.currency()).isEqualTo("INR");
    }

    @Test
    void currentWithNoSettingIsNotConfigured() {
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP)).thenReturn(Optional.empty());

        Spend s = service.current();

        assertThat(s.level()).isEqualTo("NOT_CONFIGURED");
        assertThat(s.note()).contains("Channel Settings");
    }

    @Test
    void currentWithoutBusinessAccountIdIsNotConfigured() {
        configure("{\"phoneNumberId\":\"111\"}", "tok");

        Spend s = service.current();

        assertThat(s.level()).isEqualTo("NOT_CONFIGURED");
        assertThat(s.note()).contains("Channel Settings");
    }

    @Test
    void nonNumericBudgetIsNoBudget() {
        configure("{\"businessAccountId\":\"W1\",\"monthlyBudget\":\"abc\"}", "tok");
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new PricingReport("INR", List.of(pt("10"))));

        Spend s = service.current();

        assertThat(s.level()).isEqualTo("NO_BUDGET");
        assertThat(s.budget()).isNull();
        assertThat(s.spent()).isEqualByComparingTo("10");
    }

    @Test
    void metaFailureComesBackAsANoteNotAnException() {
        configure("{\"businessAccountId\":\"W1\",\"monthlyBudget\":\"100\"}", "tok");
        when(provider.fetchPricing(any(), any(), any())).thenThrow(new ProviderSendException("(#200) permission"));

        Spend s = service.current();

        assertThat(s.spent()).isNull();
        assertThat(s.note()).contains("(#200) permission");
    }

    @Test
    void periodStartsAtMidnightIstOnTheFirstOfTheMonth() {
        configure("{\"businessAccountId\":\"W1\"}", "tok");
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new PricingReport("INR", List.of()));

        Spend s = service.current();

        assertThat(s.periodStart()).isEqualTo(Instant.parse("2026-10-31T18:30:00Z"));
        assertThat(s.periodEnd()).isEqualTo(Instant.parse("2026-11-23T04:30:00Z"));
        assertThat(s.spent()).isEqualByComparingTo("0");
    }

    @Test
    void budgetTypedWithCommasOrCurrencySymbolStillCounts() {
        for (String typed : new String[] {"5,000", "₹5000", " Rs. 5,000.50 "}) {
            configure("{\"businessAccountId\":\"W1\",\"monthlyBudget\":\"" + typed + "\"}", "tok");
            when(provider.fetchPricing(any(), any(), any())).thenReturn(new PricingReport("INR", List.of(pt("10"))));

            Spend s = service.current();

            assertThat(s.budget()).as(typed).isNotNull();
            assertThat(s.level()).as(typed).isEqualTo("OK");
        }
    }

    @Test
    void negativeBudgetTypedInSettingsIsStillNoBudget() {
        configure("{\"businessAccountId\":\"W1\",\"monthlyBudget\":\"-5\"}", "tok");
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new PricingReport("INR", List.of(pt("10"))));

        Spend s = service.current();

        assertThat(s.budget()).isNull();
        assertThat(s.level()).isEqualTo("NO_BUDGET");
    }
}
