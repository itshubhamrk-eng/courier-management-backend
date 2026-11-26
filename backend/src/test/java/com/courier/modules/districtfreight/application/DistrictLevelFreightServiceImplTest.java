package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.application.command.CreateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.application.command.UpdateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.domain.BranchLookupPort;
import com.courier.modules.districtfreight.domain.DistrictFreightStatus;
import com.courier.modules.districtfreight.domain.DistrictLevelFreight;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightRepository;
import com.courier.modules.districtfreight.domain.DistrictLookupPort;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistrictLevelFreightServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID DISTRICT = UUID.randomUUID();

    @Mock private DistrictLevelFreightRepository repository;
    @Mock private BranchLookupPort branchLookup;
    @Mock private DistrictLookupPort districtLookup;
    @Mock private AuditService auditService;

    private DistrictLevelFreightServiceImpl service;

    private static final BranchLookupPort.BranchRef ACTIVE_BRANCH =
            new BranchLookupPort.BranchRef(BRANCH, "ICHALKARANJI", "Ichalkaranji", true);
    private static final DistrictLookupPort.DistrictRef ACTIVE_DISTRICT =
            new DistrictLookupPort.DistrictRef(DISTRICT, "PUNE", "Pune", true);

    @BeforeEach
    void setUp() {
        service = new DistrictLevelFreightServiceImpl(repository, branchLookup, districtLookup, auditService);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "ops@test.com", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));

        when(branchLookup.findBranch(eq(BRANCH), eq(COMPANY))).thenReturn(Optional.of(ACTIVE_BRANCH));
        when(districtLookup.findDistrict(eq(DISTRICT))).thenReturn(Optional.of(ACTIVE_DISTRICT));
        when(repository.save(any(DistrictLevelFreight.class))).thenAnswer(inv -> {
            DistrictLevelFreight f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private CreateDistrictLevelFreightCommand createCommand() {
        return new CreateDistrictLevelFreightCommand(BRANCH, DISTRICT,
                new BigDecimal("10.00"), new BigDecimal("8.50"), new BigDecimal("8.00"),
                new BigDecimal("7.50"), new BigDecimal("6.00"), new BigDecimal("6.00"),
                true, new BigDecimal("250.00"));
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("creates an active row with the six slab rates and ODA")
    void createsRow() {
        DistrictLevelFreight saved = service.create(createCommand());

        assertThat(saved.getBranchId()).isEqualTo(BRANCH);
        assertThat(saved.getDistrictId()).isEqualTo(DISTRICT);
        assertThat(saved.getRate1To15()).isEqualByComparingTo("10.00");
        assertThat(saved.getRate1501To2000()).isEqualByComparingTo("6.00");
        assertThat(saved.getOdaCharge()).isEqualByComparingTo("250.00");
        assertThat(saved.isActive()).isTrue();
        verify(auditService).record(eq(com.courier.shared.audit.domain.AuditAction.DISTRICT_FREIGHT_CREATED),
                any(), any(), any());
    }

    @Test
    @DisplayName("refuses an unknown branch")
    void refusesUnknownBranch() {
        when(branchLookup.findBranch(eq(BRANCH), eq(COMPANY))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("refuses an inactive branch as a From Station")
    void refusesInactiveBranch() {
        when(branchLookup.findBranch(eq(BRANCH), eq(COMPANY)))
                .thenReturn(Optional.of(new BranchLookupPort.BranchRef(BRANCH, "ICHALKARANJI", "Ichalkaranji", false)));
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("refuses an inactive destination district")
    void refusesInactiveDistrict() {
        when(districtLookup.findDistrict(eq(DISTRICT)))
                .thenReturn(Optional.of(new DistrictLookupPort.DistrictRef(DISTRICT, "PUNE", "Pune", false)));
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("refuses a duplicate From Station + District combination")
    void refusesDuplicateCombo() {
        when(repository.isComboTaken(eq(COMPANY), eq(BRANCH), eq(DISTRICT), isNull())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a negative slab rate")
    void rejectsNegativeRate() {
        CreateDistrictLevelFreightCommand negative = new CreateDistrictLevelFreightCommand(
                BRANCH, DISTRICT, new BigDecimal("-1.00"), new BigDecimal("8.50"), new BigDecimal("8.00"),
                new BigDecimal("7.50"), new BigDecimal("6.00"), new BigDecimal("6.00"), true, new BigDecimal("250.00"));
        assertThatThrownBy(() -> service.create(negative)).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------- update

    @Test
    @DisplayName("updates rates and re-checks the combination, excluding itself")
    void updatesRow() {
        DistrictLevelFreight existing = existingRow();
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        UpdateDistrictLevelFreightCommand command = new UpdateDistrictLevelFreightCommand(
                BRANCH, DISTRICT, new BigDecimal("11.00"), new BigDecimal("9.00"), new BigDecimal("8.50"),
                new BigDecimal("8.00"), new BigDecimal("6.50"), new BigDecimal("6.50"), true,
                new BigDecimal("300.00"), existing.getVersion());

        DistrictLevelFreight updated = service.update(existing.getId(), command);

        assertThat(updated.getRate1To15()).isEqualByComparingTo("11.00");
        assertThat(updated.getOdaCharge()).isEqualByComparingTo("300.00");
        verify(repository).isComboTaken(COMPANY, BRANCH, DISTRICT, existing.getId());
    }

    @Test
    @DisplayName("a stale version on update is refused with an optimistic-lock conflict")
    void refusesStaleVersion() {
        DistrictLevelFreight existing = existingRow();
        existing.setVersion(5L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        UpdateDistrictLevelFreightCommand command = new UpdateDistrictLevelFreightCommand(
                BRANCH, DISTRICT, new BigDecimal("11.00"), new BigDecimal("9.00"), new BigDecimal("8.50"),
                new BigDecimal("8.00"), new BigDecimal("6.50"), new BigDecimal("6.50"), true,
                new BigDecimal("300.00"), 1L);

        assertThatThrownBy(() -> service.update(existing.getId(), command))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(repository, never()).save(any());
    }

    // -------------------------------------------------------------------- reads

    @Test
    @DisplayName("a foreign or unknown id is a 404, not a 403")
    void getByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("delete soft-deletes the row")
    void deletesRow() {
        DistrictLevelFreight existing = existingRow();
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        assertThat(existing.isDeleted()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("activate on an already-active row is a no-op")
    void activateIsIdempotent() {
        DistrictLevelFreight existing = existingRow();
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        DistrictLevelFreight result = service.activate(existing.getId());

        assertThat(result.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate then activate flips status both ways")
    void deactivateThenActivate() {
        DistrictLevelFreight existing = existingRow();
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        DistrictLevelFreight deactivated = service.deactivate(existing.getId());
        assertThat(deactivated.getStatus()).isEqualTo(DistrictFreightStatus.INACTIVE);

        DistrictLevelFreight reactivated = service.activate(existing.getId());
        assertThat(reactivated.getStatus()).isEqualTo(DistrictFreightStatus.ACTIVE);
    }

    // -------------------------------------------------------------------- helpers

    private DistrictLevelFreight existingRow() {
        DistrictLevelFreight f = new DistrictLevelFreight();
        f.setId(UUID.randomUUID());
        f.setCompanyId(COMPANY);
        f.setBranchId(BRANCH);
        f.setDistrictId(DISTRICT);
        f.setRate1To15(new BigDecimal("10.00"));
        f.setRate16To50(new BigDecimal("8.50"));
        f.setRate51To100(new BigDecimal("8.00"));
        f.setRate101To1000(new BigDecimal("7.50"));
        f.setRate1001To1500(new BigDecimal("6.00"));
        f.setRate1501To2000(new BigDecimal("6.00"));
        f.setOdaApplicable(true);
        f.setOdaCharge(new BigDecimal("250.00"));
        f.setStatus(DistrictFreightStatus.ACTIVE);
        f.setVersion(0L);
        return f;
    }

    // --------------------------------------------------------------------- copy

    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID DIST_A = UUID.randomUUID();
    private static final UUID DIST_B = UUID.randomUUID();

    private void givenCopyFixtures(Set<UUID> targetExistingDistricts) {
        when(branchLookup.findBranch(eq(TARGET), eq(COMPANY)))
                .thenReturn(Optional.of(new BranchLookupPort.BranchRef(TARGET, "KOLHAPUR", "Kolhapur", true)));
        when(districtLookup.findDistricts(any())).thenReturn(java.util.Map.of(
                DIST_A, new DistrictLookupPort.DistrictRef(DIST_A, "A", "A", true),
                DIST_B, new DistrictLookupPort.DistrictRef(DIST_B, "B", "B", true)));
        when(repository.findByCompanyIdAndBranchIdAndStatus(COMPANY, BRANCH, DistrictFreightStatus.ACTIVE))
                .thenReturn(java.util.List.of(sourceRow(DIST_A), sourceRow(DIST_B)));
        when(repository.findDistrictIdsIncludingDeleted(COMPANY, TARGET)).thenReturn(targetExistingDistricts);
    }

    private DistrictLevelFreight sourceRow(UUID districtId) {
        DistrictLevelFreight f = new DistrictLevelFreight();
        f.setBranchId(BRANCH);
        f.setDistrictId(districtId);
        f.setRate1To15(new BigDecimal("10.00"));
        f.setRate16To50(new BigDecimal("8.50"));
        f.setRate51To100(new BigDecimal("8.00"));
        f.setRate101To1000(new BigDecimal("7.50"));
        f.setRate1001To1500(new BigDecimal("6.00"));
        f.setRate1501To2000(new BigDecimal("5.50"));
        f.setOdaApplicable(false);
        f.setOdaCharge(new BigDecimal("300.00"));
        f.setStatus(DistrictFreightStatus.ACTIVE);
        return f;
    }

    @Test
    @DisplayName("copy: copies districts missing on the target, skips the ones it already has")
    void copyCopiesMissingSkipsExisting() {
        givenCopyFixtures(Set.of(DIST_A));

        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET));

        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).copied()).isEqualTo(1);
        assertThat(result.targets().get(0).skipped()).isEqualTo(1);
        org.mockito.ArgumentCaptor<DistrictLevelFreight> saved =
                org.mockito.ArgumentCaptor.forClass(DistrictLevelFreight.class);
        verify(repository).save(saved.capture());
        DistrictLevelFreight row = saved.getValue();
        assertThat(row.getBranchId()).isEqualTo(TARGET);
        assertThat(row.getDistrictId()).isEqualTo(DIST_B);
        assertThat(row.getRate1501To2000()).isEqualByComparingTo("5.50");
        assertThat(row.isOdaApplicable()).isFalse();
        assertThat(row.getOdaCharge()).isEqualByComparingTo("300.00");
        assertThat(row.isActive()).isTrue();
        verify(auditService).record(eq(com.courier.shared.audit.domain.AuditAction.DISTRICT_FREIGHT_COPIED),
                any(), any(), any());
    }

    @Test
    @DisplayName("copy: a second run copies nothing")
    void copySecondRunCopiesNothing() {
        givenCopyFixtures(Set.of(DIST_A, DIST_B));
        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET));
        assertThat(result.targets().get(0).copied()).isZero();
        assertThat(result.targets().get(0).skipped()).isEqualTo(2);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("copy: source with no active rows copies nothing, no error")
    void copyEmptySource() {
        givenCopyFixtures(Set.of());
        when(repository.findByCompanyIdAndBranchIdAndStatus(COMPANY, BRANCH, DistrictFreightStatus.ACTIVE))
                .thenReturn(java.util.List.of());
        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET));
        assertThat(result.targets().get(0).copied()).isZero();
        assertThat(result.targets().get(0).skipped()).isZero();
    }

    @Test
    @DisplayName("copy: a source district gone inactive is skipped")
    void copySkipsInactiveDistrict() {
        givenCopyFixtures(Set.of());
        when(districtLookup.findDistricts(any())).thenReturn(java.util.Map.of(
                DIST_A, new DistrictLookupPort.DistrictRef(DIST_A, "A", "A", true),
                DIST_B, new DistrictLookupPort.DistrictRef(DIST_B, "B", "B", false)));
        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET));
        assertThat(result.targets().get(0).copied()).isEqualTo(1);
        assertThat(result.targets().get(0).skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("copy: refuses source among targets, empty targets, inactive target; writes nothing")
    void copyRefusals() {
        givenCopyFixtures(Set.of());
        assertThatThrownBy(() -> service.copyFromBranch(BRANCH, java.util.List.of(BRANCH)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.copyFromBranch(BRANCH, java.util.List.of()))
                .isInstanceOf(BusinessRuleException.class);
        when(branchLookup.findBranch(eq(TARGET), eq(COMPANY)))
                .thenReturn(Optional.of(new BranchLookupPort.BranchRef(TARGET, "KOLHAPUR", "Kolhapur", false)));
        assertThatThrownBy(() -> service.copyFromBranch(BRANCH, java.util.List.of(TARGET)))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("copy: duplicate target ids are processed once")
    void copyDedupesTargets() {
        givenCopyFixtures(Set.of());
        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET, TARGET));
        assertThat(result.targets()).hasSize(1);
        assertThat(result.targets().get(0).copied()).isEqualTo(2);
    }

    @Test
    @DisplayName("copy: districts are looked up once for all targets, not once per target")
    void copyLooksUpDistrictsOnce() {
        givenCopyFixtures(Set.of());
        UUID second = UUID.randomUUID();
        when(branchLookup.findBranch(eq(second), eq(COMPANY)))
                .thenReturn(Optional.of(new BranchLookupPort.BranchRef(second, "SATARA", "Satara", true)));
        when(repository.findDistrictIdsIncludingDeleted(COMPANY, second)).thenReturn(Set.of());

        CopyDistrictFreightResult result = service.copyFromBranch(BRANCH, java.util.List.of(TARGET, second));

        assertThat(result.targets()).hasSize(2);
        verify(districtLookup, org.mockito.Mockito.times(1)).findDistricts(any());
        verify(districtLookup, never()).findDistrict(any());
    }
}
