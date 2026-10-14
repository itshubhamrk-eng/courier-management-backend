package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.ProviderSendException;
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
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyReportServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    @Mock CommunicationSettingRepository settingRepository;
    @Mock DailyReportLogRepository logRepository;
    @Mock DailyBusinessMetricsPort metricsPort;
    @Mock WhatsAppProvider whatsAppProvider;
    @Mock CommunicationTemplateService templateService;

    private CommunicationTemplate template;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DailyReportServiceImpl service;
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DailyReportServiceImpl(settingRepository, logRepository, metricsPort,
                whatsAppProvider, templateService, new TemplateRenderer("http://x/{trackingNumber}"), objectMapper);
        var seed = com.courier.modules.communication.domain.DefaultCommunicationTemplates.DAILY_SUMMARY;
        template = CommunicationTemplate.builder().eventType(seed.eventType()).channel(seed.channel())
                .templateName(seed.templateName()).content(seed.content()).build();
        when(templateService.findActive(eq(companyId), eq(CommunicationEventType.DAILY_BUSINESS_SUMMARY),
                eq(CommunicationChannel.WHATSAPP))).thenReturn(Optional.of(template));
        when(metricsPort.metricsFor(eq(companyId), eq(DAY), any(), any())).thenReturn(sampleMetrics());
        when(logRepository.saveAndFlush(any(DailyReportLog.class))).thenAnswer(i -> i.getArgument(0));
        when(logRepository.save(any(DailyReportLog.class))).thenAnswer(i -> i.getArgument(0));
        when(whatsAppProvider.send(any(), any())).thenReturn(new ProviderSendResult("wamid.X"));
    }

    private CommunicationSetting setting(String configJson) {
        CommunicationSetting s = CommunicationSetting.builder().channel(CommunicationChannel.WHATSAPP)
                .enabled(true).configJson(configJson).build();
        s.setCompanyId(companyId);
        s.setSecret("token");
        return s;
    }

    private static DailyBusinessMetrics sampleMetrics() {
        return new DailyBusinessMetrics("Acme Logistics", DAY, 1234, new BigDecimal("1234567.5"),
                new BigDecimal("50000"), 3, 20, 2, new BigDecimal("999.9"), 7, 4);
    }

    @Test
    void sendsOneMessagePerPhoneAndRecordsIt() {
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"phoneNumberId\":\"111\",\"dailyReportPhones\":\"9123456780, +91 98765 43210\"}")));
        when(logRepository.findByCompanyIdAndReportDateAndRecipient(any(), any(), any())).thenReturn(Optional.empty());

        int attempted = service.sendReportsFor(DAY);

        assertThat(attempted).isEqualTo(2);
        ArgumentCaptor<WhatsAppProvider.WhatsAppMessage> sent = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppMessage.class);
        verify(whatsAppProvider, org.mockito.Mockito.times(2)).send(sent.capture(), any());
        assertThat(sent.getAllValues()).extracting(WhatsAppProvider.WhatsAppMessage::toE164)
                .containsExactly("9123456780", "919876543210");
        assertThat(sent.getValue().approvedTemplateName()).isEqualTo("daily_business_summary");
        assertThat(sent.getValue().templateParameters()).containsExactly("Acme Logistics", "13 Oct 2026", "1,234",
                "12,34,567.50", "50,000.00", "3", "20", "2", "999.90", "7", "4");
        assertThat(sent.getValue().templateParameters()).hasSize(11);
    }

    @Test
    void usesTheTemplatesOwnNameAndContentOrder() {
        template.setTemplateName("my_summary_v2");
        template.setContent("Hi {{revenue}} and {{bookings}} then {{revenue}} bye");
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"dailyReportPhones\":\"9123456780\"}")));
        when(logRepository.findByCompanyIdAndReportDateAndRecipient(any(), any(), any())).thenReturn(Optional.empty());

        service.sendReportsFor(DAY);

        ArgumentCaptor<WhatsAppProvider.WhatsAppMessage> sent = ArgumentCaptor.forClass(WhatsAppProvider.WhatsAppMessage.class);
        verify(whatsAppProvider).send(sent.capture(), any());
        assertThat(sent.getValue().approvedTemplateName()).isEqualTo("my_summary_v2");
        assertThat(sent.getValue().templateParameters()).containsExactly("12,34,567.50", "1,234");
    }

    @Test
    void skipsWithoutBurningAttemptsWhileMetaHasNotApprovedTheTemplate() {
        template.setMetaStatus("PENDING");
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"dailyReportPhones\":\"9123456780\"}")));

        assertThat(service.sendReportsFor(DAY)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
        verify(logRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsWhenTheTemplateIsSwitchedOff() {
        when(templateService.findActive(any(), any(), any())).thenReturn(Optional.empty());
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"dailyReportPhones\":\"9123456780\"}")));

        assertThat(service.sendReportsFor(DAY)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void doesNothingWhenNoPhonesConfigured() {
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"phoneNumberId\":\"111\"}")));

        assertThat(service.sendReportsFor(DAY)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
        verify(metricsPort, never()).metricsFor(any(), any(), any(), any());
    }

    @Test
    void neverSendsTwiceForTheSameDayAndNumber() {
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"dailyReportPhones\":\"9123456780\"}")));
        DailyReportLog done = DailyReportLog.builder().reportDate(DAY).recipient("9123456780")
                .status(CommunicationStatus.SENT).attemptCount(1).build();
        when(logRepository.findByCompanyIdAndReportDateAndRecipient(companyId, DAY, "9123456780"))
                .thenReturn(Optional.of(done));

        assertThat(service.sendReportsFor(DAY)).isZero();
        verify(whatsAppProvider, never()).send(any(), any());
    }

    @Test
    void failedSendIsRecordedAndRetriedUntilTheAttemptCap() {
        when(settingRepository.findAllByChannelAndEnabledTrue(CommunicationChannel.WHATSAPP)).thenReturn(List.of(
                setting("{\"dailyReportPhones\":\"9123456780\"}")));
        when(whatsAppProvider.send(any(), any())).thenThrow(new ProviderSendException("Meta down"));
        DailyReportLog row = DailyReportLog.builder().reportDate(DAY).recipient("9123456780").build();
        when(logRepository.findByCompanyIdAndReportDateAndRecipient(companyId, DAY, "9123456780"))
                .thenReturn(Optional.of(row));

        for (int i = 0; i < DailyReportServiceImpl.MAX_ATTEMPTS + 2; i++) {
            service.sendReportsFor(DAY);
        }

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(row.getErrorMessage()).isEqualTo("Meta down");
        assertThat(row.getAttemptCount()).isEqualTo(DailyReportServiceImpl.MAX_ATTEMPTS);
        verify(whatsAppProvider, org.mockito.Mockito.times(DailyReportServiceImpl.MAX_ATTEMPTS)).send(any(), any());
    }

    @Test
    void parsePhonesKeepsDigitsDropsShortAndDuplicates() {
        assertThat(DailyReportServiceImpl.parsePhones("9123456780; 91234 56780\n+91-98765-43210, 123, "))
                .containsExactly("9123456780", "919876543210");
        assertThat(DailyReportServiceImpl.parsePhones(null)).isEmpty();
    }

    @Test
    void blankCompanyNameFallsBackAndNullMoneyIsZero() {
        DailyBusinessMetrics m = new DailyBusinessMetrics(" ", DAY, 0, null, null, 0, 0, 0, null, 0, 0);
        var v = DailyReportServiceImpl.variables(m);
        assertThat(v).containsEntry("companyName", "your company").containsEntry("revenue", "0.00")
                .containsEntry("bookings", "0");
    }

    @Test
    void indianGroupingHandlesEveryMagnitude() {
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("0"), 0)).isEqualTo("0");
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("999"), 0)).isEqualTo("999");
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("1000"), 0)).isEqualTo("1,000");
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("100000"), 2)).isEqualTo("1,00,000.00");
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("12345678.5"), 2)).isEqualTo("1,23,45,678.50");
        assertThat(DailyReportServiceImpl.indian(new BigDecimal("-1500.5"), 2)).isEqualTo("-1,500.50");
    }
}
