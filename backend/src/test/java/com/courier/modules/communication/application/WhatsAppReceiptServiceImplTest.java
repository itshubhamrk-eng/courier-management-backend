package com.courier.modules.communication.application;

import com.courier.modules.communication.application.WhatsAppReceiptService.Receipt;
import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.DailyReportLog;
import com.courier.modules.communication.domain.DailyReportLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppReceiptServiceImplTest {

    private static final Instant T1 = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-14T10:05:00Z");

    @Mock CommunicationLogRepository logRepository;
    @Mock DailyReportLogRepository dailyRepository;

    private WhatsAppReceiptServiceImpl service;
    private CommunicationLog row;

    @BeforeEach
    void setUp() {
        service = new WhatsAppReceiptServiceImpl(logRepository, dailyRepository);
        row = CommunicationLog.builder().providerMessageId("wamid.1").status(CommunicationStatus.SENT).build();
        when(logRepository.findFirstByProviderMessageId("wamid.1")).thenReturn(Optional.of(row));
        when(logRepository.findFirstByProviderMessageId("wamid.unknown")).thenReturn(Optional.empty());
        when(dailyRepository.findFirstByProviderMessageId(any())).thenReturn(Optional.empty());
    }

    private int apply(String status, Instant at, String error) {
        return service.apply(List.of(new Receipt("wamid.1", status, at, error)));
    }

    @Test
    void deliveredMovesSentToDeliveredAndStampsTheTime() {
        assertThat(apply("delivered", T1, null)).isEqualTo(1);

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        assertThat(row.getDeliveredAt()).isEqualTo(T1);
        assertThat(row.getReadAt()).isNull();
        verify(logRepository).save(row);
    }

    @Test
    void readImpliesDeliveredEvenWhenDeliveredNeverArrived() {
        apply("read", T2, null);

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        assertThat(row.getDeliveredAt()).isEqualTo(T2);
        assertThat(row.getReadAt()).isEqualTo(T2);
    }

    @Test
    void outOfOrderReceiptsNeverMoveARowBackwardOrOverwriteTimes() {
        apply("read", T2, null);
        apply("delivered", T1, null);   // arrives late
        apply("sent", T1, null);        // arrives later still

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        assertThat(row.getDeliveredAt()).isEqualTo(T2);
        assertThat(row.getReadAt()).isEqualTo(T2);
    }

    @Test
    void replayingTheSameReceiptChangesNothing() {
        apply("delivered", T1, null);
        apply("delivered", T2, null);

        assertThat(row.getDeliveredAt()).isEqualTo(T1);
    }

    @Test
    void sentReceiptIsIgnored() {
        apply("sent", T1, null);

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.SENT);
        verify(logRepository, never()).save(any());
    }

    @Test
    void failedMarksTheRowFailedWithMetasReasonAndStopsAutomaticRetry() {
        row.setNextRetryAt(T2);

        apply("failed", T1, "Message undeliverable (code 131026)");

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(row.getErrorMessage()).contains("Message undeliverable").contains("131026");
        assertThat(row.getNextRetryAt()).isNull();
    }

    @Test
    void failedAfterDeliveryIsIgnored() {
        apply("delivered", T1, null);
        apply("failed", T2, "late nonsense");

        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        assertThat(row.getErrorMessage()).isNull();
    }

    @Test
    void failedWithoutAReasonStillSaysSo() {
        apply("failed", T1, null);
        assertThat(row.getErrorMessage()).contains("no reason given");
    }

    @Test
    void aMissingTimestampFallsBackToNow() {
        apply("delivered", null, null);
        assertThat(row.getDeliveredAt()).isNotNull();
    }

    @Test
    void unknownMessagesAndGarbageAreSkippedWithoutError() {
        int matched = service.apply(java.util.Arrays.asList(
                new Receipt("wamid.unknown", "delivered", T1, null), null,
                new Receipt(null, "delivered", T1, null), new Receipt("  ", "read", T1, null),
                new Receipt("wamid.1", null, T1, null), new Receipt("wamid.1", "something_new", T1, null)));

        assertThat(matched).isEqualTo(2); // the two that named a known message, though neither changed it
        assertThat(row.getStatus()).isEqualTo(CommunicationStatus.SENT);
    }

    @Test
    void theDailySummarysReceiptsLandOnItsOwnLedger() {
        DailyReportLog daily = DailyReportLog.builder().providerMessageId("wamid.daily")
                .status(CommunicationStatus.SENT).build();
        when(logRepository.findFirstByProviderMessageId("wamid.daily")).thenReturn(Optional.empty());
        when(dailyRepository.findFirstByProviderMessageId("wamid.daily")).thenReturn(Optional.of(daily));

        service.apply(List.of(new Receipt("wamid.daily", "delivered", T1, null),
                new Receipt("wamid.daily", "read", T2, null)));

        assertThat(daily.getStatus()).isEqualTo(CommunicationStatus.DELIVERED);
        assertThat(daily.getDeliveredAt()).isEqualTo(T1);
        assertThat(daily.getReadAt()).isEqualTo(T2);
        assertThat(daily.isSent()).isTrue();   // DELIVERED must never be sent again
    }
}
