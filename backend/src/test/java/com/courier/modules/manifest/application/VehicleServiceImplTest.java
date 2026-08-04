package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.modules.manifest.domain.VehicleRepository;
import com.courier.modules.manifest.domain.VehicleStatus;
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

    @Test
    @DisplayName("create upper-cases and trims the vehicle number")
    void createNormalisesVehicleNumber() {
        when(repository.existsByCompanyIdAndVehicleNumber(eq(COMPANY), any())).thenReturn(false);

        Vehicle created = service.create(new CreateVehicleCommand(" mh12ab1234 ", null, BigDecimal.TEN, null));

        assertThat(created.getVehicleNumber()).isEqualTo("MH12AB1234");
        assertThat(created.getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    @DisplayName("create refuses a duplicate vehicle number within the company")
    void createRefusesDuplicateNumber() {
        when(repository.existsByCompanyIdAndVehicleNumber(COMPANY, "MH12AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateVehicleCommand("MH12AB1234", null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("create refuses a negative capacity")
    void createRefusesNegativeCapacity() {
        assertThatThrownBy(() -> service.create(
                new CreateVehicleCommand("MH12AB1234", null, BigDecimal.valueOf(-1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
    }
}
