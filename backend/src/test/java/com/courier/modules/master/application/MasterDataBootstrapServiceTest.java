package com.courier.modules.master.application;

import com.courier.modules.master.domain.PackageType;
import com.courier.modules.master.domain.PackageTypeRepository;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.PaymentModeRepository;
import com.courier.modules.master.domain.ServiceType;
import com.courier.modules.master.domain.ServiceTypeRepository;
import com.courier.modules.master.domain.VehicleType;
import com.courier.modules.master.domain.VehicleTypeRepository;
import com.courier.modules.master.domain.WeightSlabRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Seeding the standard catalogues, and the idempotency that makes it safe to re-run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasterDataBootstrapServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private VehicleTypeService vehicleTypes;
    @Mock private PackageTypeService packageTypes;
    @Mock private ServiceTypeService serviceTypes;
    @Mock private PaymentModeService paymentModes;
    @Mock private WeightSlabService weightSlabs;

    @Mock private VehicleTypeRepository vehicleTypeRepository;
    @Mock private PackageTypeRepository packageTypeRepository;
    @Mock private ServiceTypeRepository serviceTypeRepository;
    @Mock private PaymentModeRepository paymentModeRepository;
    @Mock private WeightSlabRepository weightSlabRepository;

    @Mock private AuditService auditService;

    private MasterDataBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new MasterDataBootstrapService(
                vehicleTypes, packageTypes, serviceTypes, paymentModes, weightSlabs,
                vehicleTypeRepository, packageTypeRepository, serviceTypeRepository,
                paymentModeRepository, weightSlabRepository, auditService);
        CompanyContext.setCompanyId(TENANT);

        when(vehicleTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(packageTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(serviceTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(paymentModeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.empty());
        when(weightSlabRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("an empty company gets the whole standard set")
    void seedsEverything() {
        MasterDataBootstrapService.BootstrapResult result = service.seedDefaults();

        assertThat(result.created())
                .containsEntry("vehicleTypes", 5)
                .containsEntry("packageTypes", 5)
                .containsEntry("serviceTypes", 4)
                .containsEntry("paymentModes", 4)
                .containsEntry("weightSlabs", 5);
        assertThat(result.skipped().values()).allMatch(count -> count == 0);

        verify(vehicleTypes, times(5)).create(any());
        verify(paymentModes, times(4)).create(any());
        verify(auditService).record(eq(AuditAction.MASTER_DATA_SEEDED), eq("MasterData"),
                isNull(), anyMap());
    }

    @Test
    @DisplayName("a second run creates nothing and reports every row as skipped")
    void isIdempotent() {
        // Running it twice must change nothing — and it must never overwrite or resurrect
        // a catalogue entry an administrator deliberately removed.
        when(vehicleTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.of(new VehicleType()));
        when(packageTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.of(new PackageType()));
        when(serviceTypeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.of(new ServiceType()));
        when(paymentModeRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.of(new PaymentMode()));
        when(weightSlabRepository.findByCodeWithinCompany(anyString(), eq(TENANT)))
                .thenReturn(Optional.of(new com.courier.modules.master.domain.WeightSlab()));

        MasterDataBootstrapService.BootstrapResult result = service.seedDefaults();

        assertThat(result.created().values()).allMatch(count -> count == 0);
        assertThat(result.skipped()).containsEntry("vehicleTypes", 5);
        verify(vehicleTypes, never()).create(any());
        verify(weightSlabs, never()).create(any());
    }

    @Test
    @DisplayName("the standard weight slabs are adjacent, never overlapping")
    void seededSlabsDoNotOverlap() {
        // If they did, every seeded company would immediately fail its own overlap rule.
        service.seedDefaults();

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.courier.modules.master.application.command.WeightSlabCommand.class);
        verify(weightSlabs, times(5)).create(captor.capture());

        var slabs = captor.getAllValues();
        for (int i = 1; i < slabs.size(); i++) {
            assertThat(slabs.get(i).minWeight())
                    .as("slab %s starts where %s ends", slabs.get(i).code(), slabs.get(i - 1).code())
                    .isEqualByComparingTo(slabs.get(i - 1).maxWeight());
        }
    }
}
