package com.courier.modules.charge.application;

import com.courier.modules.charge.application.command.CreateChargeSettingCommand;
import com.courier.modules.charge.application.command.UpdateChargeSettingCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeSetting;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Charge Setting rules — mostly the overlap guard, since shape validation lives in
 *  {@code ChargeSettingTest}. Repository, audit trail and ChargeService are mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeSettingServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();

    @Mock private ChargeSettingRepository repository;
    @Mock private ChargeService chargeService;
    @Mock private AuditService auditService;

    private ChargeSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChargeSettingServiceImpl(repository, chargeService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(repository.save(any(ChargeSetting.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(chargeService.getById(CHARGE)).thenReturn(charge());
        lenient().when(repository.findByCompanyIdAndChargeIdAndStatus(eq(COMPANY), eq(CHARGE), eq(ChargeStatus.ACTIVE)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a FACTOR setting is created with no overlap check")
    void factorCreateSucceeds() {
        ChargeSetting created = service.create(factorCommand());
        assertThat(created.getChargeType()).isEqualTo(ChargeType.FACTOR);
    }

    @Test
    @DisplayName("a setting against an unknown charge is refused")
    void unknownChargeRejected() {
        when(chargeService.getById(any())).thenThrow(new ResourceNotFoundException("Charge", CHARGE));

        assertThatThrownBy(() -> service.create(factorCommand()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No such charge");
    }

    @Test
    @DisplayName("a KG slab overlapping an existing active one is refused")
    void overlappingKgSlabRejected() {
        ChargeSetting existing = kgSetting("0", "5");
        when(repository.findByCompanyIdAndChargeIdAndStatus(COMPANY, CHARGE, ChargeStatus.ACTIVE))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(kgCommand("3", "8")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("an adjacent KG slab (touching, not overlapping) is accepted")
    void adjacentKgSlabAccepted() {
        ChargeSetting existing = kgSetting("0", "5");
        when(repository.findByCompanyIdAndChargeIdAndStatus(COMPANY, CHARGE, ChargeStatus.ACTIVE))
                .thenReturn(List.of(existing));

        ChargeSetting created = service.create(kgCommand("5", "10"));
        assertThat(created.getFromKg()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("reactivating a setting re-checks the overlap rule")
    void activateRechecksOverlap() {
        ChargeSetting toActivate = kgSetting("3", "8");
        toActivate.deactivate();
        when(repository.findByIdWithinCompany(toActivate.getId(), COMPANY)).thenReturn(Optional.of(toActivate));

        ChargeSetting conflicting = kgSetting("0", "5");
        when(repository.findByCompanyIdAndChargeIdAndStatus(COMPANY, CHARGE, ChargeStatus.ACTIVE))
                .thenReturn(List.of(conflicting));

        assertThatThrownBy(() -> service.activate(toActivate.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("an unknown id is a 404")
    void unknownIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------- helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "admin@legacy.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Charge charge() {
        Charge charge = Charge.builder().chargeName("X").serviceTypeId(UUID.randomUUID())
                .status(ChargeStatus.ACTIVE).build();
        charge.setCompanyId(COMPANY);
        charge.setId(CHARGE);
        return charge;
    }

    private static CreateChargeSettingCommand factorCommand() {
        return new CreateChargeSettingCommand(CHARGE, ChargeType.FACTOR, null,
                null, null, null, null,
                new BigDecimal("100"), ChargeValueType.AMOUNT, CommissionType.AMOUNT, BigDecimal.ZERO);
    }

    private static CreateChargeSettingCommand kgCommand(String fromKg, String toKg) {
        return new CreateChargeSettingCommand(CHARGE, ChargeType.SLAB, ChargeSlabType.KG,
                null, null, new BigDecimal(fromKg), new BigDecimal(toKg),
                new BigDecimal("50"), ChargeValueType.AMOUNT, CommissionType.AMOUNT, BigDecimal.ZERO);
    }

    private static ChargeSetting kgSetting(String fromKg, String toKg) {
        ChargeSetting setting = ChargeSetting.builder()
                .chargeId(CHARGE)
                .chargeType(ChargeType.SLAB).chargeSlabType(ChargeSlabType.KG)
                .fromKg(new BigDecimal(fromKg)).toKg(new BigDecimal(toKg))
                .chargeValue(new BigDecimal("50")).chargeValueType(ChargeValueType.AMOUNT)
                .commissionType(CommissionType.AMOUNT).commissionValue(BigDecimal.ZERO)
                .status(ChargeStatus.ACTIVE)
                .build();
        setting.setCompanyId(COMPANY);
        setting.setId(UUID.randomUUID());
        return setting;
    }
}
