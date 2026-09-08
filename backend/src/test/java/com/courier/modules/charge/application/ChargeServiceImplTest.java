package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeCommand;
import com.courier.modules.charge.application.command.UpdateChargeCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeRepository;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.ServiceType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Charge rules, with the repository, audit trail and Master's ServiceTypeService mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID SERVICE_TYPE = UUID.randomUUID();

    @Mock private ChargeRepository repository;
    @Mock private ChargeSettingRepository settingRepository;
    @Mock private ServiceTypeService serviceTypeService;
    @Mock private AuditService auditService;

    private ChargeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChargeServiceImpl(repository, settingRepository, serviceTypeService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(repository.save(any(Charge.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.isNameTaken(any(), any(), anyString(), any())).thenReturn(false);
        lenient().when(serviceTypeService.getById(SERVICE_TYPE)).thenReturn(serviceType());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a valid charge is created, ACTIVE, against an existing service type")
    void createSucceeds() {
        Charge created = service.create(new CreateChargeCommand("Fuel Surcharge", SERVICE_TYPE));

        assertThat(created.getChargeName()).isEqualTo("Fuel Surcharge");
        assertThat(created.isActive()).isTrue();
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown service type is refused")
    void unknownServiceTypeRejected() {
        when(serviceTypeService.getById(any())).thenThrow(new ResourceNotFoundException("ServiceType", SERVICE_TYPE));

        assertThatThrownBy(() -> service.create(new CreateChargeCommand("X", SERVICE_TYPE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No such service type");
    }

    @Test
    @DisplayName("a duplicate name within the same service type is refused")
    void duplicateNameRejected() {
        when(repository.isNameTaken(eq(COMPANY), eq(SERVICE_TYPE), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateChargeCommand("Fuel Surcharge", SERVICE_TYPE)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("a blank name is refused")
    void blankNameRejected() {
        assertThatThrownBy(() -> service.create(new CreateChargeCommand("  ", SERVICE_TYPE)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("update with a stale version is refused")
    void staleVersionRejected() {
        Charge existing = charge("X", 3L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new UpdateChargeCommand("Y", SERVICE_TYPE, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("an unknown id is a 404")
    void unknownIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("activate/deactivate are idempotent")
    void lifecycleIsIdempotent() {
        Charge existing = charge("X", 1L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        service.deactivate(existing.getId());
        assertThat(existing.isActive()).isFalse();
        service.deactivate(existing.getId());
        verify(auditService, times(1)).record(any(), any(), any(), any());

        service.activate(existing.getId());
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    @DisplayName("delete is refused while the charge still has live settings")
    void deleteRefusedWithLiveSettings() {
        Charge existing = charge("X", 1L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));
        when(settingRepository.countByCompanyIdAndChargeId(COMPANY, existing.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 charge setting");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("delete succeeds once no live settings remain")
    void deleteSucceedsWithNoSettings() {
        Charge existing = charge("X", 1L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));
        when(settingRepository.countByCompanyIdAndChargeId(COMPANY, existing.getId())).thenReturn(0L);
        when(repository.saveAndFlush(any(Charge.class))).thenAnswer(i -> i.getArgument(0));

        service.delete(existing.getId());

        assertThat(existing.isDeleted()).isTrue();
        verify(auditService).record(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------- helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "admin@legacy.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static ServiceType serviceType() {
        ServiceType type = new ServiceType();
        type.setCode("STD");
        type.setName("Standard");
        type.setStatus(MasterStatus.ACTIVE);
        type.setId(SERVICE_TYPE);
        return type;
    }

    private static Charge charge(String name, Long version) {
        Charge charge = Charge.builder()
                .chargeName(name)
                .serviceTypeId(SERVICE_TYPE)
                .status(ChargeStatus.ACTIVE)
                .build();
        charge.setCompanyId(COMPANY);
        charge.setId(UUID.randomUUID());
        charge.setVersion(version);
        return charge;
    }
}
