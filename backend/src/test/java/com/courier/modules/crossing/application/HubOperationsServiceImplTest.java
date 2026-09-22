package com.courier.modules.crossing.application;

import com.courier.modules.crossing.domain.HubOutScan;
import com.courier.modules.crossing.domain.HubOutScanRepository;
import com.courier.modules.crossing.domain.ShipmentException;
import com.courier.modules.crossing.domain.ShipmentExceptionCriteria;
import com.courier.modules.crossing.domain.ShipmentExceptionRepository;
import com.courier.modules.crossing.domain.ShipmentExceptionStatus;
import com.courier.modules.crossing.domain.ShipmentExceptionType;
import com.courier.modules.manifest.application.ManifestService;
import com.courier.modules.manifest.domain.DeliveryMode;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.manifest.domain.ManifestStatus;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.application.ShipmentService.MovementOutcome;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.support.application.TicketCategoryService;
import com.courier.modules.support.application.TicketService;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Out-scan (duplicate/cross-manifest/wrong-status), exceptions (raise/resolve, never
 *  touching Shipment.status), and the hub dashboard rollup — Shipment/Manifest/Ticket all
 *  mocked, mirroring {@code ManifestServiceImplTest}'s shape. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HubOperationsServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID HUB = UUID.randomUUID();

    @Mock private HubOutScanRepository hubOutScanRepository;
    @Mock private ShipmentExceptionRepository exceptionRepository;
    @Mock private ShipmentService shipmentService;
    @Mock private ManifestService manifestService;
    @Mock private TicketService ticketService;
    @Mock private TicketCategoryService ticketCategoryService;
    @Mock private AuditService auditService;

    private HubOperationsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HubOperationsServiceImpl(hubOutScanRepository, exceptionRepository, shipmentService,
                manifestService, ticketService, ticketCategoryService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "hub@test.com", Set.of(Roles.HUB_MANAGER), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(ticketCategoryService.listCategories()).thenReturn(List.of());
        when(hubOutScanRepository.save(any(HubOutScan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exceptionRepository.save(any(ShipmentException.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Manifest manifest(UUID id, ManifestStatus status) {
        Manifest m = Manifest.builder()
                .manifestNumber("MFT-250101-1234")
                .bookingBranchId(HUB)
                .deliveryBranchId(UUID.randomUUID())
                .deliveryMode(DeliveryMode.BRANCH_DELIVERY)
                .status(status)
                .build();
        m.setCompanyId(COMPANY);
        if (id != null) {
            m.setId(id);
        }
        return m;
    }

    private Shipment shipment(UUID id, UUID manifestId, ShipmentStatus status) {
        Shipment s = mock(Shipment.class);
        when(s.getId()).thenReturn(id);
        when(s.getManifestId()).thenReturn(manifestId);
        when(s.getStatus()).thenReturn(status);
        when(s.getShipmentNumber()).thenReturn("SHP-001");
        return s;
    }

    // --- out-scan ---------------------------------------------------------

    @Test
    @DisplayName("outScan rejects a tracking number that does not exist for this company")
    void outScanRejectsUnknownTrackingNumber() {
        Manifest manifest = manifest(UUID.randomUUID(), ManifestStatus.CREATED);
        when(manifestService.getById(manifest.getId())).thenReturn(manifest);
        when(shipmentService.getByTrackingNumber("TRK-404"))
                .thenThrow(new ResourceNotFoundException("Shipment", "TRK-404"));

        List<MovementOutcome> outcomes = service.outScan(manifest.getId(), HUB, List.of("TRK-404"));

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).success()).isFalse();
        assertThat(outcomes.get(0).message()).contains("No such tracking number");
        verify(hubOutScanRepository, never()).save(any());
    }

    @Test
    @DisplayName("outScan rejects a shipment attached to a different manifest")
    void outScanRejectsWrongManifest() {
        Manifest manifest = manifest(UUID.randomUUID(), ManifestStatus.CREATED);
        Shipment shipment = shipment(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.MANIFEST_CREATED);
        when(manifestService.getById(manifest.getId())).thenReturn(manifest);
        when(shipmentService.getByTrackingNumber("TRK-1")).thenReturn(shipment);

        List<MovementOutcome> outcomes = service.outScan(manifest.getId(), HUB, List.of("TRK-1"));

        assertThat(outcomes.get(0).success()).isFalse();
        assertThat(outcomes.get(0).message()).contains("Not on this Load Sheet");
        verify(hubOutScanRepository, never()).save(any());
    }

    @Test
    @DisplayName("outScan rejects a shipment not currently MANIFEST_CREATED")
    void outScanRejectsWrongStatus() {
        Manifest manifest = manifest(UUID.randomUUID(), ManifestStatus.CREATED);
        Shipment shipment = shipment(UUID.randomUUID(), manifest.getId(), ShipmentStatus.DISPATCHED);
        when(manifestService.getById(manifest.getId())).thenReturn(manifest);
        when(shipmentService.getByTrackingNumber("TRK-1")).thenReturn(shipment);

        List<MovementOutcome> outcomes = service.outScan(manifest.getId(), HUB, List.of("TRK-1"));

        assertThat(outcomes.get(0).success()).isFalse();
        assertThat(outcomes.get(0).message()).contains("Cannot be out-scanned");
    }

    @Test
    @DisplayName("outScan succeeds once, and a duplicate scan of the same shipment is rejected")
    void outScanRejectsDuplicate() {
        Manifest manifest = manifest(UUID.randomUUID(), ManifestStatus.CREATED);
        Shipment shipment = shipment(UUID.randomUUID(), manifest.getId(), ShipmentStatus.MANIFEST_CREATED);
        when(manifestService.getById(manifest.getId())).thenReturn(manifest);
        when(shipmentService.getByTrackingNumber("TRK-1")).thenReturn(shipment);
        // Pre-check, not catch-after-flush — see HubOperationsServiceImpl.outScanOne's own
        // doc: a flush exception leaves the Hibernate session unusable for the rest of a
        // bulk call's other tracking numbers, found live against real MySQL.
        when(hubOutScanRepository.existsByCompanyIdAndManifestIdAndShipmentId(
                manifest.getCompanyId(), manifest.getId(), shipment.getId()))
                .thenReturn(false, true);

        MovementOutcome first = service.outScan(manifest.getId(), HUB, List.of("TRK-1")).get(0);
        MovementOutcome second = service.outScan(manifest.getId(), HUB, List.of("TRK-1")).get(0);

        assertThat(first.success()).isTrue();
        assertThat(second.success()).isFalse();
        assertThat(second.message()).contains("Already out-scanned");
    }

    // --- exceptions ---------------------------------------------------------

    @Test
    @DisplayName("raiseException never touches Shipment.status")
    void raiseExceptionNeverTouchesShipmentStatus() {
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = shipment(shipmentId, null, ShipmentStatus.READY_FOR_MANIFEST);
        when(shipmentService.getById(shipmentId)).thenReturn(shipment);

        ShipmentException raised = service.raiseException(shipmentId, HUB, ShipmentExceptionType.DAMAGED, "dented box");

        assertThat(raised.getStatus()).isEqualTo(ShipmentExceptionStatus.OPEN);
        assertThat(raised.getExceptionType()).isEqualTo(ShipmentExceptionType.DAMAGED);
        // Only ever read the shipment — never any status-changing method.
        verify(shipmentService, never()).transitionToDispatched(any(), any(), any(), any());
        verify(shipmentService, never()).markPickedUpForDirectDelivery(any());
    }

    @Test
    @DisplayName("raiseException best-effort raises a ticket when the 'Shipment Issue' category exists")
    void raiseExceptionRaisesTicketWhenCategoryExists() {
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = shipment(shipmentId, null, ShipmentStatus.READY_FOR_MANIFEST);
        when(shipmentService.getById(shipmentId)).thenReturn(shipment);
        when(ticketCategoryService.listCategories()).thenReturn(
                List.of(TicketCategory.builder().name("Shipment Issue").active(true).build()));
        when(ticketService.create(any())).thenReturn(Ticket.builder().ticketNumber("TKT-000099").build());

        ShipmentException raised = service.raiseException(shipmentId, HUB, ShipmentExceptionType.MISSING, null);

        verify(ticketService).create(any());
        assertThat(raised).isNotNull();
    }

    @Test
    @DisplayName("raiseException skips the ticket, without failing, when no 'Shipment Issue' category exists")
    void raiseExceptionSkipsTicketWhenNoCategory() {
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = shipment(shipmentId, null, ShipmentStatus.READY_FOR_MANIFEST);
        when(shipmentService.getById(shipmentId)).thenReturn(shipment);

        ShipmentException raised = service.raiseException(shipmentId, HUB, ShipmentExceptionType.SHORT, null);

        assertThat(raised.getTicketId()).isNull();
        verify(ticketService, never()).create(any());
    }

    @Test
    @DisplayName("resolveException closes an open exception")
    void resolveExceptionHappyPath() {
        UUID id = UUID.randomUUID();
        ShipmentException open = ShipmentException.builder()
                .shipmentId(UUID.randomUUID()).hubBranchId(HUB)
                .exceptionType(ShipmentExceptionType.ON_HOLD).status(ShipmentExceptionStatus.OPEN)
                .build();
        when(exceptionRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(open));

        ShipmentException resolved = service.resolveException(id, "released");

        assertThat(resolved.getStatus()).isEqualTo(ShipmentExceptionStatus.RESOLVED);
        assertThat(resolved.getResolutionRemarks()).isEqualTo("released");
    }

    @Test
    @DisplayName("resolveException refuses an already-resolved exception")
    void resolveExceptionRefusesTwice() {
        UUID id = UUID.randomUUID();
        ShipmentException resolved = ShipmentException.builder()
                .shipmentId(UUID.randomUUID()).hubBranchId(HUB)
                .exceptionType(ShipmentExceptionType.ON_HOLD).status(ShipmentExceptionStatus.RESOLVED)
                .build();
        when(exceptionRepository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.resolveException(id, "again"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been resolved");
    }

    // --- dashboard ---------------------------------------------------------

    @Test
    @DisplayName("dashboard scopes every figure to the given hub branch")
    void dashboardScopesToHub() {
        when(shipmentService.countArrivalsAt(org.mockito.ArgumentMatchers.eq(HUB), any(), any())).thenReturn(3L);
        Page<Shipment> empty = new PageImpl<>(List.of());
        when(shipmentService.search(any(ShipmentCriteria.class), any(Pageable.class))).thenReturn(empty);
        when(manifestService.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(exceptionRepository.countByCompanyIdAndHubBranchIdAndStatus(
                COMPANY, HUB, ShipmentExceptionStatus.OPEN)).thenReturn(2L);

        HubDashboardStats stats = service.dashboard(HUB);

        assertThat(stats.todaysInbound()).isEqualTo(3L);
        assertThat(stats.pendingExceptions()).isEqualTo(2L);
        verify(shipmentService).countArrivalsAt(org.mockito.ArgumentMatchers.eq(HUB), any(), any());
    }
}
