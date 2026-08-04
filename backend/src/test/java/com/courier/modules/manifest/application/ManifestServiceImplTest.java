package com.courier.modules.manifest.application;

import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.User;
import com.courier.modules.manifest.application.command.CreateManifestCommand;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.manifest.domain.ManifestRepository;
import com.courier.modules.manifest.domain.ManifestStatus;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Manifest create/dispatch orchestration, with Shipment/Vehicle/User all mocked — mirrors
 * {@code shipment.application.ShipmentServiceImplTest}'s shape. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManifestServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BOOKING_BRANCH = UUID.randomUUID();
    private static final UUID DELIVERY_BRANCH = UUID.randomUUID();

    @Mock private ManifestRepository manifestRepository;
    @Mock private ShipmentService shipmentService;
    @Mock private VehicleService vehicleService;
    @Mock private UserService userService;
    @Mock private AuditService auditService;

    private ManifestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManifestServiceImpl(manifestRepository, shipmentService, vehicleService,
                userService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "ops@test.com", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(manifestRepository.save(any(Manifest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("create attaches every shipment id, one call per id, through the shipment module's own seam")
    void createAttachesEveryShipment() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        when(manifestRepository.existsByCompanyIdAndManifestNumber(eq(COMPANY), any())).thenReturn(false);

        Manifest created = service.create(new CreateManifestCommand(
                BOOKING_BRANCH, DELIVERY_BRANCH, List.of(s1, s2), "remarks"));

        assertThat(created.getStatus()).isEqualTo(ManifestStatus.CREATED);
        assertThat(created.getBookingBranchId()).isEqualTo(BOOKING_BRANCH);
        verify(shipmentService).attachToManifest(s1, created.getId(), BOOKING_BRANCH, DELIVERY_BRANCH);
        verify(shipmentService).attachToManifest(s2, created.getId(), BOOKING_BRANCH, DELIVERY_BRANCH);
    }

    @Test
    @DisplayName("create refuses an empty shipment list")
    void createRefusesEmptyShipmentList() {
        assertThatThrownBy(() -> service.create(
                new CreateManifestCommand(BOOKING_BRANCH, DELIVERY_BRANCH, List.of(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one shipment");
        verify(manifestRepository, never()).save(any());
    }

    @Test
    @DisplayName("dispatch refuses a manifest with no shipment")
    void dispatchRefusesWithoutShipments() {
        Manifest manifest = existingManifest(ManifestStatus.CREATED);
        when(manifestRepository.findByIdWithinCompany(manifest.getId(), COMPANY))
                .thenReturn(Optional.of(manifest));
        when(shipmentService.findManifestCreatedShipments(manifest.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.dispatch(manifest.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no shipment");
        verify(vehicleService, never()).getById(any());
    }

    @Test
    @DisplayName("dispatch refuses an inactive vehicle")
    void dispatchRefusesInactiveVehicle() {
        Manifest manifest = existingManifest(ManifestStatus.CREATED);
        UUID vehicleId = UUID.randomUUID();
        when(manifestRepository.findByIdWithinCompany(manifest.getId(), COMPANY))
                .thenReturn(Optional.of(manifest));
        when(shipmentService.findManifestCreatedShipments(manifest.getId()))
                .thenReturn(List.of(mock(Shipment.class)));
        Vehicle inactive = Vehicle.builder().vehicleNumber("MH12AB1234").status(VehicleStatus.INACTIVE).build();
        when(vehicleService.getById(vehicleId)).thenReturn(inactive);

        assertThatThrownBy(() -> service.dispatch(manifest.getId(), vehicleId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("dispatch assigns vehicle+driver, moves the manifest to DISPATCHED, and transitions its MANIFEST_CREATED shipments")
    void dispatchHappyPath() {
        Manifest manifest = existingManifest(ManifestStatus.CREATED);
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Shipment ready = mock(Shipment.class);
        when(ready.getId()).thenReturn(UUID.randomUUID());
        when(ready.getStatus()).thenReturn(ShipmentStatus.MANIFEST_CREATED);

        when(manifestRepository.findByIdWithinCompany(manifest.getId(), COMPANY))
                .thenReturn(Optional.of(manifest));
        when(shipmentService.findManifestCreatedShipments(manifest.getId())).thenReturn(List.of(ready));
        Vehicle active = Vehicle.builder().vehicleNumber("MH12AB1234").status(VehicleStatus.ACTIVE).build();
        when(vehicleService.getById(vehicleId)).thenReturn(active);
        when(userService.getById(driverId)).thenReturn(mock(User.class));

        Manifest dispatched = service.dispatch(manifest.getId(), vehicleId, driverId);

        assertThat(dispatched.getStatus()).isEqualTo(ManifestStatus.DISPATCHED);
        assertThat(dispatched.getVehicleId()).isEqualTo(vehicleId);
        assertThat(dispatched.getDriverUserId()).isEqualTo(driverId);
        verify(shipmentService).transitionToDispatched(
                List.of(ready.getId()), manifest.getId(), vehicleId, manifest.getBookingBranchId());
    }

    @Test
    @DisplayName("dispatch refuses a manifest that has already been dispatched")
    void dispatchRefusedTwice() {
        Manifest manifest = existingManifest(ManifestStatus.DISPATCHED);
        when(manifestRepository.findByIdWithinCompany(manifest.getId(), COMPANY))
                .thenReturn(Optional.of(manifest));

        assertThatThrownBy(() -> service.dispatch(manifest.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been dispatched");
        verify(shipmentService, never()).findManifestCreatedShipments(any());
    }

    private Manifest existingManifest(ManifestStatus status) {
        Manifest manifest = Manifest.builder()
                .manifestNumber("MFT-250101-1234")
                .bookingBranchId(BOOKING_BRANCH)
                .deliveryBranchId(DELIVERY_BRANCH)
                .status(status)
                .build();
        manifest.setCompanyId(COMPANY);
        return manifest;
    }
}
