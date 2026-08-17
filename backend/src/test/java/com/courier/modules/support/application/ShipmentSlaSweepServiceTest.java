package com.courier.modules.support.application;

import com.courier.modules.support.domain.ShipmentSlaBreachRepository;
import com.courier.modules.support.domain.ShipmentSlaConfig;
import com.courier.modules.support.domain.ShipmentSlaPort;
import com.courier.modules.support.domain.ShipmentSlaStage;
import com.courier.modules.support.domain.ShipmentSlaThresholds;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketDirectoryPort;
import com.courier.modules.support.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentSlaSweepServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID SHIPMENT = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Mock private TicketDirectoryPort directory;
    @Mock private ShipmentSlaPort shipmentSlaPort;
    @Mock private ShipmentSlaBreachRepository breachRepository;
    @Mock private TicketCategoryRepository categoryRepository;
    @Mock private TicketService ticketService;

    private ShipmentSlaSweepService service;

    @BeforeEach
    void setUp() {
        service = new ShipmentSlaSweepService(directory, shipmentSlaPort, breachRepository,
                categoryRepository, ticketService);

        when(directory.listActiveCompanyIds()).thenReturn(List.of(COMPANY));
        when(directory.shipmentSlaSettings(COMPANY)).thenReturn(
                new ShipmentSlaConfig(true, new ShipmentSlaThresholds(24, 24, 48, 12, 12)));
        when(categoryRepository.findByNameIgnoreCase("SLA Breach")).thenReturn(
                Optional.of(TicketCategory.builder().name("SLA Breach").active(true).build()));
        when(directory.managerOfBranch(BRANCH, COMPANY)).thenReturn(Optional.of(MANAGER));

        Ticket raised = Ticket.builder()
                .ticketNumber("TKT-000099")
                .status(TicketStatus.OPEN)
                .build();
        when(ticketService.raiseSystemTicket(any(), any())).thenReturn(raised);
    }

    private ShipmentSlaPort.Candidate candidate(ShipmentSlaStage stage) {
        return new ShipmentSlaPort.Candidate(SHIPMENT, "PUNE-000001", BRANCH, stage,
                Instant.now().minusSeconds(3600 * 30), 30);
    }

    @Test
    @DisplayName("a fresh breach raises exactly one ticket, assigned to the branch manager")
    void raisesTicketForFreshBreach() {
        when(shipmentSlaPort.findBreachCandidates(eq(COMPANY), any(), any()))
                .thenReturn(List.of(candidate(ShipmentSlaStage.BOOKING_TO_LOADING_SHEET)));
        when(breachRepository.existsByCompanyIdAndShipmentIdAndStage(
                COMPANY, SHIPMENT, ShipmentSlaStage.BOOKING_TO_LOADING_SHEET)).thenReturn(false);

        service.sweepAllCompanies();

        verify(ticketService, times(1)).raiseSystemTicket(any(), eq(MANAGER));
        verify(breachRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("a shipment already ticketed for this stage is never raised again")
    void skipsAlreadyBreachedStage() {
        when(shipmentSlaPort.findBreachCandidates(eq(COMPANY), any(), any()))
                .thenReturn(List.of(candidate(ShipmentSlaStage.THC_TO_INSCAN)));
        when(breachRepository.existsByCompanyIdAndShipmentIdAndStage(
                COMPANY, SHIPMENT, ShipmentSlaStage.THC_TO_INSCAN)).thenReturn(true);

        service.sweepAllCompanies();

        verify(ticketService, never()).raiseSystemTicket(any(), any());
    }

    @Test
    @DisplayName("a company with the sweep disabled is skipped entirely")
    void skipsDisabledCompany() {
        when(directory.shipmentSlaSettings(COMPANY)).thenReturn(
                new ShipmentSlaConfig(false, new ShipmentSlaThresholds(24, 24, 48, 12, 12)));

        service.sweepAllCompanies();

        verify(shipmentSlaPort, never()).findBreachCandidates(any(), any(), any());
        verify(ticketService, never()).raiseSystemTicket(any(), any());
    }

    @Test
    @DisplayName("one company's sweep failure does not stop the next company's")
    void oneCompanyFailureDoesNotAbortTheSweep() {
        UUID otherCompany = UUID.randomUUID();
        when(directory.listActiveCompanyIds()).thenReturn(List.of(COMPANY, otherCompany));
        when(directory.shipmentSlaSettings(COMPANY)).thenThrow(new RuntimeException("boom"));
        when(directory.shipmentSlaSettings(otherCompany)).thenReturn(
                new ShipmentSlaConfig(true, new ShipmentSlaThresholds(24, 24, 48, 12, 12)));
        when(shipmentSlaPort.findBreachCandidates(eq(otherCompany), any(), any()))
                .thenReturn(List.of());

        service.sweepAllCompanies();

        verify(directory, times(1)).shipmentSlaSettings(otherCompany);
    }
}
