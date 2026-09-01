package com.courier.modules.freight.application;

import com.courier.modules.distance.application.AddressDistanceService;
import com.courier.modules.distance.domain.AddressDistance;
import com.courier.modules.distance.domain.AddressType;
import com.courier.modules.freight.application.command.CreateFreightFactorCommand;
import com.courier.modules.freight.application.command.FreightCalculationCommand;
import com.courier.modules.freight.application.command.UpdateFreightFactorCommand;
import com.courier.modules.freight.domain.FreightFactor;
import com.courier.modules.freight.domain.FreightFactorRepository;
import com.courier.modules.freight.domain.FreightFactorStatus;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Freight factor rules, with the repository, audit trail and AddressDistanceService
 * mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreightFactorServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID FROM_BRANCH = UUID.randomUUID();
    private static final UUID TO_BRANCH = UUID.randomUUID();

    @Mock private FreightFactorRepository repository;
    @Mock private AddressDistanceService addressDistanceService;
    @Mock private AuditService auditService;

    private FreightFactorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FreightFactorServiceImpl(repository, addressDistanceService, auditService);
        CompanyContext.setCompanyId(COMPANY);
        signedIn(Roles.COMPANY_ADMIN);

        when(repository.save(any(FreightFactor.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByCompanyIdAndStatus(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("a valid cell is created with no overlap")
    void createSucceeds() {
        FreightFactor created = service.create(createCommand("0.000", "100.000", "0.000", "10.000"));

        assertThat(created.isActive()).isTrue();
        assertThat(created.getFactor()).isEqualByComparingTo("5.00");
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an invalid range is refused before touching the repository")
    void invalidRangeRejected() {
        assertThatThrownBy(() -> service.create(createCommand("100.000", "100.000", "0.000", "10.000")))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a cell whose both ranges overlap an existing active cell is refused")
    void overlapRejected() {
        FreightFactor existing = existingCell("0.000", "100.000", "5.000", "15.000");
        when(repository.findByCompanyIdAndStatus(COMPANY, FreightFactorStatus.ACTIVE))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(createCommand("50.000", "150.000", "0.000", "10.000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("overlap on only one axis is accepted")
    void oneAxisOverlapAccepted() {
        // Same distance band as candidate, but disjoint weight band -> no 2D conflict.
        FreightFactor existing = existingCell("0.000", "100.000", "10.001", "20.000");
        when(repository.findByCompanyIdAndStatus(COMPANY, FreightFactorStatus.ACTIVE))
                .thenReturn(List.of(existing));

        FreightFactor created = service.create(createCommand("0.000", "100.000", "0.000", "10.000"));

        assertThat(created.isActive()).isTrue();
    }

    // ------------------------------------------------------------------- update

    @Test
    @DisplayName("a stale version on update is refused")
    void staleVersionRejected() {
        FreightFactor existing = existingCell("0.000", "100.000", "0.000", "10.000");
        existing.setVersion(3L);
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new UpdateFreightFactorCommand(new BigDecimal("0.000"), new BigDecimal("50.000"),
                        new BigDecimal("0.000"), new BigDecimal("10.000"), new BigDecimal("5.00"), 1L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("activating an already-active cell is idempotent")
    void activateIdempotent() {
        FreightFactor existing = existingCell("0.000", "100.000", "0.000", "10.000");
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        FreightFactor activated = service.activate(existing.getId());

        assertThat(activated.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate then reactivate flips status")
    void deactivateThenActivate() {
        FreightFactor existing = existingCell("0.000", "100.000", "0.000", "10.000");
        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(existing));

        FreightFactor deactivated = service.deactivate(existing.getId());
        assertThat(deactivated.getStatus()).isEqualTo(FreightFactorStatus.INACTIVE);

        when(repository.findByIdWithinCompany(existing.getId(), COMPANY)).thenReturn(Optional.of(deactivated));
        FreightFactor reactivated = service.activate(existing.getId());
        assertThat(reactivated.getStatus()).isEqualTo(FreightFactorStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ calculate

    @Test
    @DisplayName("freight = matched factor * weight")
    void calculateHappyPath() {
        FreightFactor cell = existingCell("0.000", "100.000", "0.000", "10.000");
        cell.setFactor(new BigDecimal("12.50"));
        when(repository.findByCompanyIdAndStatus(COMPANY, FreightFactorStatus.ACTIVE))
                .thenReturn(List.of(cell));
        when(addressDistanceService.resolveBranchDistance(FROM_BRANCH, TO_BRANCH))
                .thenReturn(distance("45.000"));

        FreightCalculationResult result = service.calculate(
                new FreightCalculationCommand(FROM_BRANCH, TO_BRANCH, new BigDecimal("4.000")));

        assertThat(result.matchedFactor()).isSameAs(cell);
        assertThat(result.distanceKm()).isEqualByComparingTo("45.000");
        // 12.50 * 4.000 = 50.00
        assertThat(result.freight()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("a gap in the configured grid matches nothing")
    void calculateGapRejected() {
        FreightFactor low = existingCell("0.000", "50.000", "0.000", "10.000");
        FreightFactor high = existingCell("100.000", "200.000", "0.000", "10.000");
        when(repository.findByCompanyIdAndStatus(COMPANY, FreightFactorStatus.ACTIVE))
                .thenReturn(List.of(low, high));
        when(addressDistanceService.resolveBranchDistance(FROM_BRANCH, TO_BRANCH))
                .thenReturn(distance("75.000"));

        assertThatThrownBy(() -> service.calculate(
                new FreightCalculationCommand(FROM_BRANCH, TO_BRANCH, new BigDecimal("4.000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("gap");
    }

    @Test
    @DisplayName("a zero or negative weight is refused")
    void calculateZeroWeightRejected() {
        assertThatThrownBy(() -> service.calculate(
                new FreightCalculationCommand(FROM_BRANCH, TO_BRANCH, BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    // -------------------------------------------------------------------- helpers

    private void signedIn(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "admin@legacy.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static AddressDistance distance(String km) {
        return AddressDistance.builder()
                .addressType(AddressType.BRANCH)
                .fromId(FROM_BRANCH)
                .toId(TO_BRANCH)
                .distanceKm(new BigDecimal(km))
                .build();
    }

    private static CreateFreightFactorCommand createCommand(String fromKm, String toKm,
                                                             String fromWeight, String toWeight) {
        return new CreateFreightFactorCommand(new BigDecimal(fromKm), new BigDecimal(toKm),
                new BigDecimal(fromWeight), new BigDecimal(toWeight), new BigDecimal("5.00"));
    }

    private static FreightFactor existingCell(String fromKm, String toKm, String fromWeight, String toWeight) {
        FreightFactor cell = FreightFactor.builder()
                .fromKm(new BigDecimal(fromKm))
                .toKm(new BigDecimal(toKm))
                .fromWeight(new BigDecimal(fromWeight))
                .toWeight(new BigDecimal(toWeight))
                .factor(new BigDecimal("5.00"))
                .status(FreightFactorStatus.ACTIVE)
                .build();
        cell.setCompanyId(COMPANY);
        cell.setVersion(0L);
        return cell;
    }
}
