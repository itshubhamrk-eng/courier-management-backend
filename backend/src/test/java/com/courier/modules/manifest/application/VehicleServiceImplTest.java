package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.application.command.UpdateVehicleCommand;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.manifest.domain.VehicleRepository;
import com.courier.modules.manifest.domain.VehicleStatus;
import com.courier.modules.manifest.domain.VehicleType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private VehicleRepository repository;
    @Mock private AuditService auditService;

    private VehicleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VehicleServiceImpl(repository, auditService);
        CompanyContext.setCompanyId(COMPANY);
        when(repository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private static CreateVehicleCommand createCommand(String vehicleNumber, VehicleType vehicleType,
                                                        BigDecimal capacityKg) {
        return new CreateVehicleCommand(vehicleNumber, vehicleType, null, null, null, capacityKg,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("create upper-cases and trims the vehicle number")
    void createNormalisesVehicleNumber() {
        when(repository.existsByCompanyIdAndVehicleNumber(eq(COMPANY), any())).thenReturn(false);

        Vehicle created = service.create(createCommand(" mh12ab1234 ", VehicleType.TRUCK, BigDecimal.TEN));

        assertThat(created.getVehicleNumber()).isEqualTo("MH12AB1234");
        assertThat(created.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    @DisplayName("create refuses a duplicate vehicle number within the company")
    void createRefusesDuplicateNumber() {
        when(repository.existsByCompanyIdAndVehicleNumber(COMPANY, "MH12AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand("MH12AB1234", VehicleType.TRUCK, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("create refuses a negative capacity")
    void createRefusesNegativeCapacity() {
        assertThatThrownBy(() -> service.create(
                createCommand("MH12AB1234", VehicleType.TRUCK, BigDecimal.valueOf(-1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("deactivate flips active, not the operational status")
    void deactivateFlipsActiveOnly() {
        Vehicle vehicle = Vehicle.builder().vehicleNumber("MH12AB1234").vehicleType(VehicleType.TRUCK)
                .status(VehicleStatus.AVAILABLE).build();
        when(repository.findByIdWithinCompany(any(), eq(COMPANY))).thenReturn(java.util.Optional.of(vehicle));

        Vehicle result = service.deactivate(UUID.randomUUID());

        assertThat(result.isActive()).isFalse();
        assertThat(result.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("update replaces editable fields and checks the version")
    void updateReplacesFields() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().vehicleNumber("MH12AB1234").vehicleType(VehicleType.TRUCK)
                .status(VehicleStatus.AVAILABLE).build();
        vehicle.setVersion(0L);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(vehicle));
        when(repository.existsByCompanyIdAndVehicleNumber(any(), any())).thenReturn(false);

        UpdateVehicleCommand command = new UpdateVehicleCommand("MH12AB1234", VehicleType.VAN, null, null,
                null, null, null, null, null, null, null, null, null, VehicleStatus.MAINTENANCE, null, null, 0L);
        Vehicle updated = service.update(id, command);

        assertThat(updated.getVehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(updated.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    }
}
