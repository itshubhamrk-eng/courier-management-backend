package com.courier.modules.communication.application;

import com.courier.modules.communication.application.WhatsAppRateSyncService.Derived;
import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.application.provider.ProviderSendException;
import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.application.provider.WhatsAppProvider.PricingPoint;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationSettingRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppRateSyncServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock CommunicationSettingRepository settingRepository;
    @Mock CommunicationSettingService settingService;
    @Mock WhatsAppProvider provider;

    private WhatsAppRateSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppRateSyncServiceImpl(settingRepository, settingService, provider, new ObjectMapper());
        CompanyContext.setCompanyId(COMPANY);
        CommunicationSetting setting = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP).enabled(true)
                .provider("META_CLOUD_API")
                .configJson("{\"phoneNumberId\":\"111\",\"businessAccountId\":\"WABA9\",\"dailyReportPhones\":\"9123456780\"}").build();
        setting.setSecret("tok");
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP)).thenReturn(Optional.of(setting));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private static PricingPoint pt(String category, String type, long volume, String cost) {
        return new PricingPoint(category, type, volume, new BigDecimal(cost));
    }

    // ---------------------------------------------------------------- the arithmetic

    @Test
    void rateIsBilledCostDividedByDeliveredMessagesSummedAcrossDays() {
        List<Derived> d = WhatsAppRateSyncServiceImpl.derive(List.of(
                pt("UTILITY", "REGULAR", 600, "69"), pt("UTILITY", "REGULAR", 400, "46"),
                pt("MARKETING", "REGULAR", 10, "7.84")));

        assertThat(d).hasSize(2);
        assertThat(d.get(0).category()).isEqualTo("UTILITY");
        assertThat(d.get(0).volume()).isEqualTo(1000);
        assertThat(d.get(0).cost()).isEqualByComparingTo("115");
        assertThat(d.get(0).rate()).isEqualByComparingTo("0.1150");
        assertThat(d.get(1).rate()).isEqualByComparingTo("0.7840");
    }

    @Test
    void freeMessagesAreIgnoredSoTheyCannotDragTheRateToZero() {
        List<Derived> d = WhatsAppRateSyncServiceImpl.derive(List.of(
                pt("UTILITY", "REGULAR", 100, "11.5"),
                pt("UTILITY", "FREE_CUSTOMER_SERVICE", 900, "0"),
                pt("UTILITY", "FREE_ENTRY_POINT", 50, "0")));

        assertThat(d).hasSize(1);
        assertThat(d.get(0).volume()).isEqualTo(100);
        assertThat(d.get(0).rate()).isEqualByComparingTo("0.1150");
    }

    @Test
    void marketingLiteFoldsIntoMarketingButServiceReferralAndInternationalAreLeftOut() {
        List<Derived> d = WhatsAppRateSyncServiceImpl.derive(List.of(
                pt("MARKETING", "REGULAR", 5, "4"), pt("MARKETING_LITE", "REGULAR", 5, "2"),
                pt("SERVICE", "REGULAR", 99, "99"), pt("REFERRAL_CONVERSION", "REGULAR", 9, "9"),
                pt("AUTHENTICATION_INTERNATIONAL", "REGULAR", 9, "9")));

        assertThat(d).hasSize(1);
        assertThat(d.get(0).category()).isEqualTo("MARKETING");
        assertThat(d.get(0).volume()).isEqualTo(10);
        assertThat(d.get(0).rate()).isEqualByComparingTo("0.6000");
    }

    @Test
    void aCategoryWithMessagesButNoCostOrNoMessagesGetsNoRate() {
        assertThat(WhatsAppRateSyncServiceImpl.derive(List.of(
                pt("UTILITY", "REGULAR", 50, "0"), pt("MARKETING", "REGULAR", 0, "5")))).isEmpty();
        assertThat(WhatsAppRateSyncServiceImpl.derive(List.of())).isEmpty();
        assertThat(WhatsAppRateSyncServiceImpl.derive(List.of(new PricingPoint(null, "REGULAR", 1, BigDecimal.ONE)))).isEmpty();
    }

    // ---------------------------------------------------------------- the flow

    @Test
    void savesDerivedRatesAndCurrencyKeepingEveryOtherSettingAndTheToken() {
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new WhatsAppProvider.PricingReport("INR", List.of(
                pt("UTILITY", "REGULAR", 1000, "115"), pt("AUTHENTICATION", "REGULAR", 200, "23"))));

        var result = service.fetchAndSave();

        ArgumentCaptor<UpsertCommunicationSettingCommand> cmd = ArgumentCaptor.forClass(UpsertCommunicationSettingCommand.class);
        verify(settingService).upsert(cmd.capture());
        var saved = cmd.getValue();
        assertThat(saved.channel()).isEqualTo(CommunicationChannel.WHATSAPP);
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.provider()).isEqualTo("META_CLOUD_API");
        assertThat(saved.secret()).isNull();                                   // a blank secret keeps the stored token
        assertThat(saved.config()).containsEntry("rateUtility", "0.115").containsEntry("rateAuthentication", "0.115")
                .containsEntry("rateCurrency", "INR")
                .containsEntry("phoneNumberId", "111").containsEntry("businessAccountId", "WABA9")
                .containsEntry("dailyReportPhones", "9123456780")
                .doesNotContainKey("rateMarketing");                           // never billed -> untouched
        assertThat(result.saved()).containsExactly("UTILITY", "AUTHENTICATION");
        assertThat(result.billedTotal()).isEqualByComparingTo("138");
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.days()).isEqualTo(WhatsAppRateSyncService.LOOKBACK_DAYS);
    }

    @Test
    void readsTheLastNinetyDaysUsingTheCompanysOwnCredentials() {
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new WhatsAppProvider.PricingReport("INR", List.of()));
        var from = ArgumentCaptor.forClass(Instant.class);
        var to = ArgumentCaptor.forClass(Instant.class);
        var creds = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppCredentials.class);

        service.fetchAndSave();

        verify(provider).fetchPricing(creds.capture(), from.capture(), to.capture());
        assertThat(creds.getValue().businessAccountId()).isEqualTo("WABA9");
        assertThat(creds.getValue().accessToken()).isEqualTo("tok");
        assertThat(java.time.Duration.between(from.getValue(), to.getValue()).toDays()).isEqualTo(90);
    }

    @Test
    void whenMetaHasBilledNothingItSaysSoAndChangesNothing() {
        when(provider.fetchPricing(any(), any(), any())).thenReturn(new WhatsAppProvider.PricingReport("INR", List.of(
                pt("UTILITY", "FREE_CUSTOMER_SERVICE", 5, "0"))));

        var result = service.fetchAndSave();

        assertThat(result.rates()).isEmpty();
        assertThat(result.saved()).isEmpty();
        assertThat(result.note()).contains("not billed any messages").contains("test number");
        verify(settingService, never()).upsert(any());
    }

    @Test
    void aMetaFailureReachesTheAdminAsAReadableError() {
        when(provider.fetchPricing(any(), any(), any())).thenThrow(
                new ProviderSendException("Meta billing lookup failed: (#200) permission denied (code 200)"));

        assertThatThrownBy(() -> service.fetchAndSave()).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("permission denied");
        verify(settingService, never()).upsert(any());
    }

    @Test
    void needsAConfiguredWhatsAppAccount() {
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.fetchAndSave()).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not configured");

        CommunicationSetting noWaba = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP)
                .enabled(true).configJson("{\"phoneNumberId\":\"111\"}").build();
        noWaba.setSecret("tok");
        when(settingRepository.findByCompanyIdAndChannel(COMPANY, CommunicationChannel.WHATSAPP)).thenReturn(Optional.of(noWaba));
        assertThatThrownBy(() -> service.fetchAndSave()).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Business Account ID");
        verify(provider, never()).fetchPricing(any(), any(), any());
    }
}
