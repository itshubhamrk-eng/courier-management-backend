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

    private CommunicationDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunicationDashboardServiceImpl(repository);
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
        when(repository.countTodayByChannelAndStatus(any(), any())).thenReturn(List.of(
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
        when(repository.countTodayByChannelAndStatus(any(), any())).thenReturn(List.of());

        var summary = service.today();

        assertThat(summary.totalSent()).isZero();
        assertThat(summary.channels()).hasSize(3);
        assertThat(summary.channels().values()).allSatisfy(c -> assertThat(c.sent()).isZero());
    }
}
