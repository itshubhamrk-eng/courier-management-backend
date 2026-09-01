package com.courier.modules.communication.application;

import com.courier.modules.communication.application.command.CreateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpdateCommunicationTemplateCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.CommunicationTemplateRepository;
import com.courier.modules.communication.domain.TemplateStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunicationTemplateServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private CommunicationTemplateRepository repository;
    @Mock private AuditService auditService;

    private CommunicationTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommunicationTemplateServiceImpl(repository, new TemplateRenderer(
                "http://localhost:4200/track/{trackingNumber}"), auditService);
        CompanyContext.setCompanyId(COMPANY);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void list_seedsTwelveDefaultRowsWhenCompanyHasNone() {
        when(repository.countByCompanyId(COMPANY)).thenReturn(0L);
        when(repository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(COMPANY)).thenReturn(List.of());

        service.list();

        // 4 default events x 3 channels
        verify(repository, times(12)).save(any());
    }

    @Test
    void list_doesNotReseedWhenTemplatesAlreadyExist() {
        when(repository.countByCompanyId(COMPANY)).thenReturn(12L);
        when(repository.findAllByCompanyIdOrderByEventTypeAscChannelAsc(COMPANY)).thenReturn(List.of());

        service.list();

        verify(repository, times(0)).save(any());
    }

    @Test
    void create_refusesADuplicateEventChannelCombination() {
        when(repository.findByCompanyIdAndEventTypeAndChannel(COMPANY, CommunicationEventType.SHIPMENT_BOOKED,
                CommunicationChannel.WHATSAPP)).thenReturn(Optional.of(CommunicationTemplate.builder().build()));

        assertThatThrownBy(() -> service.create(new CreateCommunicationTemplateCommand(
                CommunicationEventType.SHIPMENT_BOOKED, CommunicationChannel.WHATSAPP, "t", null, "c")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_succeedsForANewCombination() {
        when(repository.findByCompanyIdAndEventTypeAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        CommunicationTemplate created = service.create(new CreateCommunicationTemplateCommand(
                CommunicationEventType.SHIPMENT_CANCELLED, CommunicationChannel.SMS, "Cancelled SMS", null,
                "Sorry, {{shipmentNumber}} was cancelled."));

        assertThat(created.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(created.getCompanyId()).isEqualTo(COMPANY);
    }

    @Test
    void update_refusesAStaleVersion() {
        CommunicationTemplate existing = CommunicationTemplate.builder()
                .eventType(CommunicationEventType.SHIPMENT_BOOKED).channel(CommunicationChannel.SMS)
                .templateName("old").content("old content").status(TemplateStatus.ACTIVE).build();
        existing.setCompanyId(COMPANY);
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(existing));
        // version defaults to null on a transient entity — force a mismatch via a non-null expectation
        existing.setVersion(5L);

        assertThatThrownBy(() -> service.update(id, new UpdateCommunicationTemplateCommand(
                "new", null, "new content", TemplateStatus.ACTIVE, 4L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
