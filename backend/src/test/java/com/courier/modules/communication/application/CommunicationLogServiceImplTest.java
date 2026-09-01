package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CommunicationLogServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private CommunicationLogRepository repository;
    private AuditService auditService;
    private CommunicationLogServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(CommunicationLogRepository.class);
        auditService = org.mockito.Mockito.mock(AuditService.class);
        service = new CommunicationLogServiceImpl(repository, auditService);
        CompanyContext.setCompanyId(COMPANY);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private CommunicationLog row(CommunicationStatus status) {
        CommunicationLog row = CommunicationLog.builder()
                .shipmentId(UUID.randomUUID()).eventType(CommunicationEventType.SHIPMENT_BOOKED)
                .channel(CommunicationChannel.SMS).recipient("9876500000").status(status).build();
        row.setCompanyId(COMPANY);
        return row;
    }

    @Test
    void retry_requeuesAFailedRowAsPending() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(row(CommunicationStatus.FAILED)));

        CommunicationLog result = service.retry(id);

        assertThat(result.getStatus()).isEqualTo(CommunicationStatus.PENDING);
        assertThat(result.getNextRetryAt()).isNull();
    }

    @Test
    void retry_refusesANonFailedRow() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(row(CommunicationStatus.SENT)));

        assertThatThrownBy(() -> service.retry(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void companyIsolation_aForeignCompanysLogRow404s() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
