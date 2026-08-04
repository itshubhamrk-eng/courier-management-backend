package com.courier.modules.master.application;

import com.courier.modules.master.application.command.RouteCommand;
import com.courier.modules.master.domain.BranchLookupPort;
import com.courier.modules.master.domain.DistanceUnit;
import com.courier.modules.master.domain.Route;
import com.courier.modules.master.domain.RouteRepository;
import com.courier.modules.master.infrastructure.MasterTable;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Route rules: the branch pair, its direction, and the seam onto {@code modules/company}.
 *
 * <p>Both endpoints are checked through {@link BranchLookupPort}, which takes the company
 * explicitly — so an id from another company simply does not resolve and is refused as
 * unknown rather than linked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private RouteRepository repository;
    @Mock private BranchLookupPort branches;
    @Mock private MasterUniquenessChecker uniqueness;
    @Mock private AuditService auditService;

    private RouteServiceImpl service;
    private UUID pune;
    private UUID mumbai;

    @BeforeEach
    void setUp() {
        service = new RouteServiceImpl(repository, branches, uniqueness, auditService);
        CompanyContext.setCompanyId(TENANT);
        signedIn();

        pune = UUID.randomUUID();
        mumbai = UUID.randomUUID();
        when(branches.findBranch(pune, TENANT)).thenReturn(Optional.of(
                new BranchLookupPort.BranchRef(pune, "PUNE_MAIN", "Pune Main", true)));
        when(branches.findBranch(mumbai, TENANT)).thenReturn(Optional.of(
                new BranchLookupPort.BranchRef(mumbai, "BOM_CENTRAL", "Mumbai Central", true)));

        when(repository.saveAndFlush(any(Route.class))).thenAnswer(i -> i.getArgument(0));
        when(uniqueness.isCodeTaken(anyString(), any(), any(), anyString())).thenReturn(false);
        when(uniqueness.isTaken(anyString(), any(), any(), anyMap())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a route between two active branches of the company is created")
    void createsRoute() {
        Route created = service.create(command("PNQ_BOM", pune, mumbai));

        assertThat(created.getBookingBranchId()).isEqualTo(pune);
        assertThat(created.getDeliveryBranchId()).isEqualTo(mumbai);
        assertThat(created.getTransitDays()).isEqualTo(1);
        assertThat(created.getTransitHours()).isEqualTo(0);
        assertThat(created.getDistanceUnit()).isEqualTo(DistanceUnit.KM);
    }

    @Test
    @DisplayName("transit hours of 24 or more is refused — that belongs in transit days")
    void transitHoursOutOfRangeRefused() {
        assertThatThrownBy(() -> service.create(new RouteCommand(
                "PNQ_BOM", "Pune to Mumbai", null, 0, pune, mumbai,
                new BigDecimal("150.00"), null, 1, 24, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Transit hours must be between 0 and 23");
    }

    @Test
    @DisplayName("a negative transit hours is refused")
    void transitHoursNegativeRefused() {
        assertThatThrownBy(() -> service.create(new RouteCommand(
                "PNQ_BOM", "Pune to Mumbai", null, 0, pune, mumbai,
                new BigDecimal("150.00"), null, 1, -1, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Transit hours must be between 0 and 23");
    }

    @Test
    @DisplayName("a branch from another company is refused as unknown")
    void foreignBranchRefused() {
        UUID foreign = UUID.randomUUID();
        when(branches.findBranch(foreign, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command("X", foreign, mumbai)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No branch of this company");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an inactive branch cannot be an end of a new route")
    void inactiveBranchRefused() {
        when(branches.findBranch(mumbai, TENANT)).thenReturn(Optional.of(
                new BranchLookupPort.BranchRef(mumbai, "BOM_CENTRAL", "Mumbai Central", false)));

        assertThatThrownBy(() -> service.create(command("PNQ_BOM", pune, mumbai)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive and cannot be the delivery end");
    }

    @Test
    @DisplayName("an existing route survives its branch being deactivated")
    void editSurvivesInactiveBranch() {
        // The shipments already on the lane still have to be delivered.
        Route existing = existing("PNQ_BOM", pune, mumbai);
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));
        when(branches.findBranch(mumbai, TENANT)).thenReturn(Optional.of(
                new BranchLookupPort.BranchRef(mumbai, "BOM_CENTRAL", "Mumbai Central", false)));

        Route updated = service.update(existing.getId(), new RouteCommand(
                null, "Pune to Mumbai overnight", null, 0, pune, mumbai,
                new BigDecimal("150.00"), null, 1, null, null, 2L));

        assertThat(updated.getName()).isEqualTo("Pune to Mumbai overnight");
    }

    @Test
    @DisplayName("the two ends must differ")
    void sameBranchRefused() {
        assertThatThrownBy(() -> service.create(command("LOOP", pune, pune)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    @DisplayName("a second route for the same ordered pair is refused")
    void duplicatePairRefused() {
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("booking_branch_id", pune);
        pair.put("delivery_branch_id", mumbai);
        when(uniqueness.isTaken(eq(MasterTable.ROUTES), eq(TENANT), isNull(), eq(pair)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(command("PNQ_BOM_2", pune, mumbai)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists between these two branches");
    }

    @Test
    @DisplayName("the reverse direction is a different route and is allowed")
    void reverseDirectionAllowed() {
        // Same kilometres, usually different transit days: the outbound leaves on the
        // night line-haul and the return does not.
        Map<String, Object> forward = new LinkedHashMap<>();
        forward.put("booking_branch_id", pune);
        forward.put("delivery_branch_id", mumbai);
        when(uniqueness.isTaken(eq(MasterTable.ROUTES), eq(TENANT), isNull(), eq(forward)))
                .thenReturn(true);

        Route reverse = service.create(command("BOM_PNQ", mumbai, pune));

        assertThat(reverse.getBookingBranchId()).isEqualTo(mumbai);
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), TENANT, "admin@legacy.test",
                Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static RouteCommand command(String code, UUID booking, UUID delivery) {
        return new RouteCommand(code, code, null, 0, booking, delivery,
                new BigDecimal("150.00"), null, null, null, null, null);
    }

    private static Route existing(String code, UUID booking, UUID delivery) {
        Route route = new Route();
        route.setCode(code);
        route.setName(code);
        route.setBookingBranchId(booking);
        route.setDeliveryBranchId(delivery);
        route.setCompanyId(TENANT);
        route.setVersion(2L);
        return route;
    }
}
