package com.courier.modules.crossing.application;

import com.courier.modules.crossing.domain.CrossingBranchDirectoryPort;
import com.courier.modules.crossing.domain.CrossingDetail;
import com.courier.modules.crossing.domain.CrossingDetailRepository;
import com.courier.modules.crossing.domain.CrossingStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossingServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID SHIPMENT = UUID.randomUUID();
    private static final UUID HUB_1 = UUID.randomUUID();
    private static final UUID HUB_2 = UUID.randomUUID();
    private static final UUID HUB_3 = UUID.randomUUID();

    @Mock private CrossingDetailRepository repository;
    @Mock private CrossingBranchDirectoryPort branchDirectory;
    @Mock private AuditService auditService;

    private CrossingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CrossingServiceImpl(repository, branchDirectory, auditService);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "ops@test.com", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(branchDirectory.findBranch(any(), any()))
                .thenReturn(Optional.of(new CrossingBranchDirectoryPort.BranchRef(
                        UUID.randomUUID(), COMPANY, "HUB", "Hub", true)));
        when(repository.save(any(CrossingDetail.class))).thenAnswer(inv -> {
            CrossingDetail d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createLegs writes one row per branch, in order, charge on hop 0 only")
    void createLegsWritesOrderedHops() {
        List<CrossingDetail> legs = service.createLegs(SHIPMENT, List.of(HUB_1, HUB_2, HUB_3), new BigDecimal("90"));

        assertThat(legs).hasSize(3);
        assertThat(legs.get(0).getSequenceOrder()).isZero();
        assertThat(legs.get(0).getBranchId()).isEqualTo(HUB_1);
        assertThat(legs.get(0).getCharge()).isEqualByComparingTo("90");
        assertThat(legs.get(1).getSequenceOrder()).isEqualTo(1);
        assertThat(legs.get(1).getBranchId()).isEqualTo(HUB_2);
        assertThat(legs.get(1).getCharge()).isEqualByComparingTo("0");
        assertThat(legs.get(2).getSequenceOrder()).isEqualTo(2);
        assertThat(legs.get(2).getBranchId()).isEqualTo(HUB_3);
        assertThat(legs).allMatch(l -> l.getStatus() == CrossingStatus.PENDING);
    }

    @Test
    @DisplayName("createLegs refuses an empty route")
    void createLegsRefusesEmpty() {
        assertThatThrownBy(() -> service.createLegs(SHIPMENT, List.of(), BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("createLegs 404s a branch foreign to this company")
    void createLegsRejectsForeignBranch() {
        when(branchDirectory.findBranch(HUB_1, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLegs(SHIPMENT, List.of(HUB_1), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("arriveAt a middle hop completes it and returns the next hop's branch")
    void arriveAtMiddleHopReturnsNext() {
        List<CrossingDetail> route = threeHopRoute();
        when(repository.findByShipmentWithinCompany(SHIPMENT, COMPANY)).thenReturn(route);

        Optional<UUID> next = service.arriveAt(SHIPMENT, HUB_1);

        assertThat(next).contains(HUB_2);
        assertThat(route.get(0).getStatus()).isEqualTo(CrossingStatus.COMPLETED);
        assertThat(route.get(1).getStatus()).isEqualTo(CrossingStatus.PENDING);
    }

    @Test
    @DisplayName("arriveAt the last hop completes it and returns empty — no further crossing stop")
    void arriveAtLastHopReturnsEmpty() {
        List<CrossingDetail> route = threeHopRoute();
        route.get(0).setStatus(CrossingStatus.COMPLETED);
        route.get(1).setStatus(CrossingStatus.COMPLETED);
        when(repository.findByShipmentWithinCompany(SHIPMENT, COMPANY)).thenReturn(route);

        Optional<UUID> next = service.arriveAt(SHIPMENT, HUB_3);

        assertThat(next).isEmpty();
        assertThat(route.get(2).getStatus()).isEqualTo(CrossingStatus.COMPLETED);
    }

    @Test
    @DisplayName("arriveAt a shipment with no crossing route at all is a no-op, returns empty")
    void arriveAtNoRouteIsNoop() {
        when(repository.findByShipmentWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());

        assertThat(service.arriveAt(SHIPMENT, HUB_1)).isEmpty();
    }

    @Test
    @DisplayName("arriveAt the wrong branch (out of sequence) is refused")
    void arriveAtWrongBranchRejected() {
        List<CrossingDetail> route = threeHopRoute();
        when(repository.findByShipmentWithinCompany(SHIPMENT, COMPANY)).thenReturn(route);

        assertThatThrownBy(() -> service.arriveAt(SHIPMENT, HUB_3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not expected");
    }

    private static List<CrossingDetail> threeHopRoute() {
        List<CrossingDetail> route = new java.util.ArrayList<>(List.of(
                CrossingDetail.builder().sequenceOrder(0).branchId(HUB_1)
                        .status(CrossingStatus.PENDING).charge(BigDecimal.TEN).build(),
                CrossingDetail.builder().sequenceOrder(1).branchId(HUB_2)
                        .status(CrossingStatus.PENDING).charge(BigDecimal.ZERO).build(),
                CrossingDetail.builder().sequenceOrder(2).branchId(HUB_3)
                        .status(CrossingStatus.PENDING).charge(BigDecimal.ZERO).build()));
        route.forEach(hop -> hop.setId(UUID.randomUUID()));
        return route;
    }
}
