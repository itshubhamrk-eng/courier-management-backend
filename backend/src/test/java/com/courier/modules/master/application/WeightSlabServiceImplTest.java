package com.courier.modules.master.application;

import com.courier.modules.master.application.command.WeightSlabCommand;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.WeightSlab;
import com.courier.modules.master.domain.WeightSlabRepository;
import com.courier.modules.master.domain.WeightUnit;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The overlap rule.
 *
 * <p>MySQL has no exclusion constraint, so this is the only thing standing between a
 * tariff and two active slabs that both claim 2 kg — which would price two identical
 * shipments differently depending on row order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeightSlabServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private WeightSlabRepository repository;
    @Mock private MasterUniquenessChecker uniqueness;
    @Mock private AuditService auditService;

    private WeightSlabServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WeightSlabServiceImpl(repository, uniqueness, auditService);
        CompanyContext.setCompanyId(TENANT);
        signedIn();

        when(repository.saveAndFlush(any(WeightSlab.class))).thenAnswer(i -> i.getArgument(0));
        when(uniqueness.isCodeTaken(anyString(), any(), any(), anyString())).thenReturn(false);
        when(uniqueness.isTaken(anyString(), any(), any(), anyMap())).thenReturn(false);
        when(repository.findByCompanyIdAndWeightUnitAndStatus(any(), any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("an overlapping slab is refused, naming the one it collides with")
    void overlapRefused() {
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.KG, MasterStatus.ACTIVE))
                .thenReturn(List.of(slab("SLAB_1_5", "1.000", "5.000")));

        assertThatThrownBy(() -> service.create(command("SLAB_4_10", "4.000", "10.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps SLAB_1_5");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an exactly adjacent slab is accepted")
    void adjacentAccepted() {
        // [0,1) and [1,5) is what every real tariff looks like.
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.KG, MasterStatus.ACTIVE))
                .thenReturn(List.of(slab("SLAB_0_1", "0.000", "1.000")));

        WeightSlab created = service.create(command("SLAB_1_5", "1.000", "5.000"));

        assertThat(created.getMinWeight()).isEqualByComparingTo("1.000");
    }

    @Test
    @DisplayName("a slab in another unit is not an overlap")
    void differentUnitIgnored() {
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.GRAM, MasterStatus.ACTIVE))
                .thenReturn(List.of());

        WeightSlabCommand grams = new WeightSlabCommand("G_1_5", "1 to 5 g", null, 0,
                new BigDecimal("1.000"), new BigDecimal("5.000"), WeightUnit.GRAM, null);

        assertThat(service.create(grams).getWeightUnit()).isEqualTo(WeightUnit.GRAM);
    }

    @Test
    @DisplayName("a slab does not overlap itself when it is edited")
    void ignoresItself() {
        WeightSlab existing = slab("SLAB_1_5", "1.000", "5.000");
        existing.setCompanyId(TENANT);
        existing.setVersion(1L);
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.KG, MasterStatus.ACTIVE))
                .thenReturn(List.of(existing));

        WeightSlab updated = service.update(existing.getId(),
                new WeightSlabCommand(null, "1 to 6 kg", null, 0,
                        new BigDecimal("1.000"), new BigDecimal("6.000"), WeightUnit.KG, 1L));

        assertThat(updated.getMaxWeight()).isEqualByComparingTo("6.000");
    }

    @Test
    @DisplayName("the overlap rule runs on activation too")
    void checkedOnActivation() {
        // Deactivate a slab, add an overlapping one, reactivate the first: without this
        // check that sequence walks straight around the rule.
        WeightSlab dormant = slab("SLAB_1_5", "1.000", "5.000");
        dormant.setCompanyId(TENANT);
        dormant.deactivate();
        when(repository.findByIdWithinCompany(dormant.getId(), TENANT))
                .thenReturn(Optional.of(dormant));
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.KG, MasterStatus.ACTIVE))
                .thenReturn(List.of(slab("SLAB_2_8", "2.000", "8.000")));

        assertThatThrownBy(() -> service.activate(dormant.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps SLAB_2_8");
    }

    @Test
    @DisplayName("an inactive slab is not checked for overlap when it is merely edited")
    void inactiveSlabsMayOverlap() {
        // A superseded tariff has to be allowed to sit alongside the one that replaced it.
        WeightSlab dormant = slab("OLD", "1.000", "5.000");
        dormant.setCompanyId(TENANT);
        dormant.deactivate();
        dormant.setVersion(1L);
        when(repository.findByIdWithinCompany(dormant.getId(), TENANT))
                .thenReturn(Optional.of(dormant));
        when(repository.findByCompanyIdAndWeightUnitAndStatus(TENANT, WeightUnit.KG, MasterStatus.ACTIVE))
                .thenReturn(List.of(slab("NEW", "0.000", "10.000")));

        WeightSlab updated = service.update(dormant.getId(),
                new WeightSlabCommand(null, "Old band", null, 0,
                        new BigDecimal("1.000"), new BigDecimal("5.000"), WeightUnit.KG, 1L));

        assertThat(updated.getStatus()).isEqualTo(MasterStatus.INACTIVE);
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), TENANT, "admin@legacy.test",
                Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static WeightSlabCommand command(String code, String min, String max) {
        return new WeightSlabCommand(code, code, null, 0,
                new BigDecimal(min), new BigDecimal(max), WeightUnit.KG, null);
    }

    private static WeightSlab slab(String code, String min, String max) {
        WeightSlab slab = new WeightSlab();
        slab.setCode(code);
        slab.setName(code);
        slab.setMinWeight(new BigDecimal(min));
        slab.setMaxWeight(new BigDecimal(max));
        slab.setWeightUnit(WeightUnit.KG);
        return slab;
    }
}
