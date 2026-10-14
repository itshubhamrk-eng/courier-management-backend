package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunicationDashboardServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private CommunicationLogRepository repository;
    @Mock private com.courier.modules.communication.domain.DailyReportLogRepository dailyReportLogRepository;
    @Mock private com.courier.modules.communication.domain.CommunicationSettingRepository settingRepository;
    @Mock private com.courier.modules.communication.domain.CommunicationTemplateRepository templateRepository;

    private CommunicationDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunicationDashboardServiceImpl(repository, dailyReportLogRepository, settingRepository,
                templateRepository, new com.fasterxml.jackson.databind.ObjectMapper());
        when(dailyReportLogRepository.findFirstByCompanyIdOrderByReportDateDescCreatedAtDesc(any()))
                .thenReturn(java.util.Optional.empty());
        when(settingRepository.findByCompanyIdAndChannel(any(), any())).thenReturn(java.util.Optional.empty());
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private CommunicationLogRepository.ChannelStatusCount row(CommunicationChannel channel,
                                                                CommunicationStatus status, long total) {
        return new CommunicationLogRepository.ChannelStatusCount() {
            public CommunicationChannel getChannel() {
                return channel;
            }
            public CommunicationStatus getStatus() {
                return status;
            }
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void deliveredFoldsIntoSentTotals_matchingTheBriefsWorkedExample() {
        when(repository.countByChannelAndStatusBetween(any(), any(), any())).thenReturn(List.of(
                row(CommunicationChannel.WHATSAPP, CommunicationStatus.SENT, 50),
                row(CommunicationChannel.WHATSAPP, CommunicationStatus.DELIVERED, 1150),
                row(CommunicationChannel.WHATSAPP, CommunicationStatus.FAILED, 50)));

        var summary = service.today();

        assertThat(summary.channels().get(CommunicationChannel.WHATSAPP).sent()).isEqualTo(1200);
        assertThat(summary.channels().get(CommunicationChannel.WHATSAPP).delivered()).isEqualTo(1150);
        assertThat(summary.channels().get(CommunicationChannel.WHATSAPP).failed()).isEqualTo(50);
        assertThat(summary.totalSent()).isEqualTo(1200);
        assertThat(summary.totalDelivered()).isEqualTo(1150);
    }

    @Test
    void emptyDay_everyCountIsZeroNotNull() {
        when(repository.countByChannelAndStatusBetween(any(), any(), any())).thenReturn(List.of());

        var summary = service.today();

        assertThat(summary.totalSent()).isZero();
        assertThat(summary.channels()).hasSize(3);
        assertThat(summary.channels().values()).allSatisfy(c -> assertThat(c.sent()).isZero());
    }

    // ------------------------------------------------------------------ periods, rates, extras

    @Test
    void periodWindowsAreCutAtMidnightInTheReportingZone() {
        var captured = org.mockito.ArgumentCaptor.forClass(Instant.class);
        when(repository.countByChannelAndStatusBetween(any(), any(), any())).thenReturn(List.of());

        var today = service.summary(CommunicationDashboardService.Period.TODAY);
        var yesterday = service.summary(CommunicationDashboardService.Period.YESTERDAY);
        var week = service.summary(CommunicationDashboardService.Period.LAST_7_DAYS);
        var month = service.summary(CommunicationDashboardService.Period.LAST_30_DAYS);

        var ist = java.time.ZoneId.of("Asia/Kolkata");
        var day = java.time.LocalDate.now(ist);
        assertThat(today.from()).isEqualTo(day.atStartOfDay(ist).toInstant());
        assertThat(today.to()).isEqualTo(day.plusDays(1).atStartOfDay(ist).toInstant());
        assertThat(yesterday.from()).isEqualTo(day.minusDays(1).atStartOfDay(ist).toInstant());
        assertThat(yesterday.to()).isEqualTo(day.atStartOfDay(ist).toInstant());
        assertThat(week.daily()).hasSize(7);
        assertThat(month.daily()).hasSize(30);
        assertThat(today.daily()).hasSize(1);
        assertThat(yesterday.daily()).hasSize(1);
        assertThat(week.daily().get(6).date()).isEqualTo(day);
    }

    @Test
    void deliveryAndReadRatesAreOneDecimalAndNullWhenNothingWasSent() {
        when(repository.countByChannelAndStatusBetween(any(), any(), any())).thenReturn(List.of(
                row(CommunicationChannel.WHATSAPP, CommunicationStatus.SENT, 1),
                row(CommunicationChannel.WHATSAPP, CommunicationStatus.DELIVERED, 2)));
        when(repository.countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndReadAtNotNull(any(), any(), any()))
                .thenReturn(1L);

        var s = service.summary(CommunicationDashboardService.Period.TODAY);
        assertThat(s.totalRead()).isEqualTo(1);
        assertThat(s.deliveryRatePercent()).isEqualTo(66.7);
        assertThat(s.readRatePercent()).isEqualTo(33.3);

        when(repository.countByChannelAndStatusBetween(any(), any(), any())).thenReturn(List.of());
        var empty = service.summary(CommunicationDashboardService.Period.TODAY);
        assertThat(empty.deliveryRatePercent()).isNull();
        assertThat(empty.readRatePercent()).isNull();
    }

    @Test
    void dailySeriesBucketsByIstDayAndZeroFillsQuietDays() {
        var ist = java.time.ZoneId.of("Asia/Kolkata");
        var today = java.time.LocalDate.now(ist);
        // 23:30 IST yesterday is still "yesterday" in India even though it is already today in UTC? No —
        // 18:00 UTC = 23:30 IST: same IST day. 19:00 UTC = 00:30 IST next day. Both prove the zone is IST.
        Instant lateYesterday = today.minusDays(1).atTime(23, 30).atZone(ist).toInstant();
        Instant earlyToday = today.atTime(0, 30).atZone(ist).toInstant();
        when(repository.findSeriesRows(any(), any(), any())).thenReturn(List.of(
                series(lateYesterday, CommunicationStatus.SENT), series(earlyToday, CommunicationStatus.DELIVERED),
                series(earlyToday, CommunicationStatus.FAILED)));

        var s = service.summary(CommunicationDashboardService.Period.LAST_7_DAYS);

        var y = s.daily().get(5);
        var t = s.daily().get(6);
        assertThat(y.date()).isEqualTo(today.minusDays(1));
        assertThat(y.sent()).isEqualTo(1);
        assertThat(t.sent()).isEqualTo(1);       // delivered counts as sent
        assertThat(t.delivered()).isEqualTo(1);
        assertThat(t.failed()).isEqualTo(1);
        assertThat(s.daily().subList(0, 5)).allSatisfy(p -> assertThat(p.sent() + p.failed()).isZero());
    }

    private static CommunicationLogRepository.SeriesRow series(Instant at, CommunicationStatus status) {
        return new CommunicationLogRepository.SeriesRow() {
            public Instant getAt() {
                return at;
            }
            public CommunicationStatus getStatus() {
                return status;
            }
        };
    }

    @Test
    void templateHealthCountsWhatsAppByMetaStatusAndEveryChannelSwitchedOff() {
        java.util.function.BiFunction<CommunicationChannel, String, com.courier.modules.communication.domain.CommunicationTemplate> t =
                (channel, meta) -> com.courier.modules.communication.domain.CommunicationTemplate.builder()
                        .channel(channel).metaStatus(meta).build();
        var off = t.apply(CommunicationChannel.SMS, null);
        off.setStatus(com.courier.modules.communication.domain.TemplateStatus.INACTIVE);
        when(templateRepository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(any())).thenReturn(List.of(
                t.apply(CommunicationChannel.WHATSAPP, "APPROVED"), t.apply(CommunicationChannel.WHATSAPP, "PENDING"),
                t.apply(CommunicationChannel.WHATSAPP, "REJECTED"), t.apply(CommunicationChannel.WHATSAPP, "PAUSED"),
                t.apply(CommunicationChannel.WHATSAPP, null), t.apply(CommunicationChannel.EMAIL, null), off));

        var h = service.summary(CommunicationDashboardService.Period.TODAY).templates();

        assertThat(h.approved()).isEqualTo(1);
        assertThat(h.pending()).isEqualTo(1);
        assertThat(h.rejected()).isEqualTo(2);
        assertThat(h.notSubmitted()).isEqualTo(1);
        assertThat(h.switchedOff()).isEqualTo(1);
        assertThat(h.total()).isEqualTo(7);
    }

    @Test
    void dailySummaryStatusReportsConfiguredNumbersAndTheLastRun() {
        var setting = com.courier.modules.communication.domain.CommunicationSetting.builder()
                .channel(CommunicationChannel.WHATSAPP).enabled(true)
                .configJson("{\"dailyReportPhones\":\"9123456780, 9876543210\"}").build();
        when(settingRepository.findByCompanyIdAndChannel(any(), org.mockito.ArgumentMatchers.eq(CommunicationChannel.WHATSAPP)))
                .thenReturn(java.util.Optional.of(setting));
        var last = com.courier.modules.communication.domain.DailyReportLog.builder()
                .reportDate(java.time.LocalDate.of(2026, 10, 13)).recipient("9123456780")
                .status(CommunicationStatus.DELIVERED).build();
        last.setDeliveredAt(Instant.parse("2026-10-14T03:31:00Z"));
        when(dailyReportLogRepository.findFirstByCompanyIdOrderByReportDateDescCreatedAtDesc(any()))
                .thenReturn(java.util.Optional.of(last));

        var d = service.summary(CommunicationDashboardService.Period.TODAY).dailyReport();

        assertThat(d.configured()).isTrue();
        assertThat(d.recipients()).isEqualTo(2);
        assertThat(d.lastStatus()).isEqualTo("DELIVERED");
        assertThat(d.lastDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 13));
        assertThat(d.lastDeliveredAt()).isEqualTo(Instant.parse("2026-10-14T03:31:00Z"));
    }

    @Test
    void dailySummaryIsNotConfiguredWhenWhatsAppHasNoNumbersOrIsOff() {
        var d = service.summary(CommunicationDashboardService.Period.TODAY).dailyReport();
        assertThat(d.configured()).isFalse();
        assertThat(d.recipients()).isZero();
        assertThat(d.lastStatus()).isNull();
    }

    @Test
    void recentFailuresCarryTheReasonAndTheLastAttemptTime() {
        var failed = com.courier.modules.communication.domain.CommunicationLog.builder()
                .shipmentId(UUID.randomUUID()).channel(CommunicationChannel.WHATSAPP)
                .eventType(com.courier.modules.communication.domain.CommunicationEventType.SHIPMENT_BOOKED)
                .recipient("9123456780").status(CommunicationStatus.FAILED).errorMessage("Template not approved")
                .attemptCount(2).lastAttemptAt(Instant.parse("2026-10-14T05:00:00Z")).build();
        when(repository.findTop8ByCompanyIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByUpdatedAtDesc(
                any(), org.mockito.ArgumentMatchers.eq(CommunicationStatus.FAILED), any(), any())).thenReturn(List.of(failed));

        var rows = service.summary(CommunicationDashboardService.Period.TODAY).recentFailures();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).errorMessage()).isEqualTo("Template not approved");
        assertThat(rows.get(0).attemptCount()).isEqualTo(2);
        assertThat(rows.get(0).at()).isEqualTo(Instant.parse("2026-10-14T05:00:00Z"));
    }

    @Test
    void lastReceiptAtIsPassedThroughSoTheUiCanTellWhetherTheWebhookWorks() {
        when(repository.findLastDeliveredAt(any())).thenReturn(Instant.parse("2026-10-14T06:00:00Z"));
        assertThat(service.today().lastReceiptAt()).isEqualTo(Instant.parse("2026-10-14T06:00:00Z"));
        when(repository.findLastDeliveredAt(any())).thenReturn(null);
        assertThat(service.today().lastReceiptAt()).isNull();
    }

    // ------------------------------------------------------------------ cost estimate

    private static CommunicationLogRepository.CategoryCount cat(String category, long total) {
        return new CommunicationLogRepository.CategoryCount() {
            public String getCategory() {
                return category;
            }
            public long getTotal() {
                return total;
            }
        };
    }

    private void whatsAppConfig(String json) {
        var setting = com.courier.modules.communication.domain.CommunicationSetting.builder()
                .channel(CommunicationChannel.WHATSAPP).enabled(true).configJson(json).build();
        when(settingRepository.findByCompanyIdAndChannel(any(), org.mockito.ArgumentMatchers.eq(CommunicationChannel.WHATSAPP)))
                .thenReturn(java.util.Optional.of(setting));
    }

    @Test
    void costIsMessagesTimesTheCompanysOwnRatePerCategory() {
        whatsAppConfig("{\"rateUtility\":\"0.115\",\"rateMarketing\":\"0.78\"}");
        when(repository.countWhatsAppByCategory(any(), any(), any(), any())).thenReturn(List.of(
                cat("UTILITY", 1000), cat("MARKETING", 10)));

        var cost = service.today().whatsAppCost();

        assertThat(cost.lines()).hasSize(2);
        assertThat(cost.lines().get(0).category()).isEqualTo("UTILITY");
        assertThat(cost.lines().get(0).amount()).isEqualByComparingTo("115.00");
        assertThat(cost.lines().get(1).amount()).isEqualByComparingTo("7.80");
        assertThat(cost.total()).isEqualByComparingTo("122.80");
        assertThat(cost.anyRateSet()).isTrue();
        assertThat(cost.ratesComplete()).isTrue();
    }

    @Test
    void withoutRatesTheMessagesAreCountedButNothingIsPriced() {
        when(repository.countWhatsAppByCategory(any(), any(), any(), any())).thenReturn(List.of(cat("UTILITY", 40)));

        var cost = service.today().whatsAppCost();

        assertThat(cost.anyRateSet()).isFalse();
        assertThat(cost.ratesComplete()).isFalse();
        assertThat(cost.lines()).hasSize(1);
        assertThat(cost.lines().get(0).messages()).isEqualTo(40);
        assertThat(cost.lines().get(0).rate()).isNull();
        assertThat(cost.lines().get(0).amount()).isNull();
        assertThat(cost.total()).isEqualByComparingTo("0");
    }

    @Test
    void aMissingRateOnlyBlanksItsOwnLineAndMarksTheTotalIncomplete() {
        whatsAppConfig("{\"rateUtility\":\"1\"}");
        when(repository.countWhatsAppByCategory(any(), any(), any(), any())).thenReturn(List.of(
                cat("UTILITY", 3), cat("MARKETING", 2)));

        var cost = service.today().whatsAppCost();

        assertThat(cost.total()).isEqualByComparingTo("3.00");
        assertThat(cost.ratesComplete()).isFalse();
        assertThat(cost.lines().get(1).amount()).isNull();
    }

    @Test
    void anUnusableRateIsTreatedAsNotSetNotAsAnError() {
        assertThat(CommunicationDashboardServiceImpl.rate("0.115")).isEqualByComparingTo("0.115");
        assertThat(CommunicationDashboardServiceImpl.rate(" 1,250.50 ")).isEqualByComparingTo("1250.50");
        assertThat(CommunicationDashboardServiceImpl.rate("0")).isEqualByComparingTo("0");
        assertThat(CommunicationDashboardServiceImpl.rate("-1")).isNull();
        assertThat(CommunicationDashboardServiceImpl.rate("abc")).isNull();
        assertThat(CommunicationDashboardServiceImpl.rate("")).isNull();
        assertThat(CommunicationDashboardServiceImpl.rate(null)).isNull();
    }

    @Test
    void theBasisIsSentUntilReceiptsAreLiveThenDelivered() {
        var statuses = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        when(repository.countWhatsAppByCategory(any(), statuses.capture(), any(), any())).thenReturn(List.of());

        var before = service.today().whatsAppCost();
        assertThat(before.basis()).isEqualTo("SENT");
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(CommunicationStatus.SENT, CommunicationStatus.DELIVERED);

        when(repository.findLastDeliveredAt(any())).thenReturn(Instant.parse("2026-10-14T06:00:00Z"));
        var after = service.today().whatsAppCost();
        assertThat(after.basis()).isEqualTo("DELIVERED");
        assertThat(statuses.getValue()).containsExactly(CommunicationStatus.DELIVERED);
    }

    @Test
    void theDailySummaryIsPricedUnderItsTemplatesCategory() {
        whatsAppConfig("{\"rateUtility\":\"0.10\",\"rateMarketing\":\"0.50\"}");
        when(repository.countWhatsAppByCategory(any(), any(), any(), any())).thenReturn(List.of(cat("UTILITY", 10)));
        when(dailyReportLogRepository.countByCompanyIdAndStatusInAndSentAtGreaterThanEqualAndSentAtLessThan(any(), any(), any(), any()))
                .thenReturn(4L);
        var daily = com.courier.modules.communication.domain.CommunicationTemplate.builder()
                .eventType(com.courier.modules.communication.domain.CommunicationEventType.DAILY_BUSINESS_SUMMARY)
                .channel(CommunicationChannel.WHATSAPP).category("MARKETING").build();
        when(templateRepository.findByCompanyIdAndEventTypeAndChannel(any(), any(), any()))
                .thenReturn(java.util.Optional.of(daily));

        var cost = service.today().whatsAppCost();

        assertThat(cost.lines().get(0).messages()).isEqualTo(10);               // UTILITY
        assertThat(cost.lines().get(1).category()).isEqualTo("MARKETING");
        assertThat(cost.lines().get(1).messages()).isEqualTo(4);
        assertThat(cost.total()).isEqualByComparingTo("3.00");                  // 10*0.10 + 4*0.50
    }

    @Test
    void anEmptyPeriodHasNoLinesAndAZeroTotal() {
        var cost = service.today().whatsAppCost();
        assertThat(cost.lines()).isEmpty();
        assertThat(cost.total()).isEqualByComparingTo("0");
        assertThat(cost.ratesComplete()).isTrue();
    }

    @Test
    void costCurrencyDefaultsToRupeesAndFollowsTheRateCurrencySetting() {
        assertThat(service.today().whatsAppCost().currency()).isEqualTo("INR");

        whatsAppConfig("{\"rateCurrency\":\" usd \"}");
        assertThat(service.today().whatsAppCost().currency()).isEqualTo("USD");

        whatsAppConfig("{\"rateCurrency\":\"\"}");
        assertThat(service.today().whatsAppCost().currency()).isEqualTo("INR");
    }
}
