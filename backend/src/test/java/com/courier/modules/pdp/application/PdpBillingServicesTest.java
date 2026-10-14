package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.User;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.districtfreight.domain.PincodeCoverageLookupPort;
import com.courier.modules.districtfreight.domain.PincodeCoverageLookupPort.CoverageRef;
import com.courier.modules.pdp.application.PdpRateService.Quote;
import com.courier.modules.pdp.application.PdpStatementService.StatementView;
import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpEarningRepository;
import com.courier.modules.pdp.domain.PdpEarningStatus;
import com.courier.modules.pdp.domain.PdpEarningType;
import com.courier.modules.pdp.domain.PdpRateType;
import com.courier.modules.pdp.domain.PdpStatement;
import com.courier.modules.pdp.domain.PdpStatementRepository;
import com.courier.modules.pdp.domain.PdpStatementStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdpBillingServicesTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();
    private static final UUID DISTRICT = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @Mock private PdpEarningRepository earningRepo;
    @Mock private PdpStatementRepository statementRepo;
    @Mock private PdpRateService rateService;
    @Mock private PincodeCoverageLookupPort coverage;
    @Mock private BranchService branchService;
    @Mock private AuditService auditService;
    @Mock private UserService userService;

    private PdpEarningServiceImpl earningService;
    private PdpStatementServiceImpl statementService;
    private final List<PdpEarning> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        earningService = new PdpEarningServiceImpl(earningRepo, rateService, coverage, auditService, userService);
        statementService = new PdpStatementServiceImpl(statementRepo, earningRepo, branchService, auditService, userService);
        CompanyContext.setCompanyId(COMPANY);
        when(coverage.findByPincode("411001")).thenReturn(Optional.of(new CoverageRef(UUID.randomUUID(), "411001",
                true, true, DISTRICT, "PUN", "Pune", true, "Pune", UUID.randomUUID())));
        when(earningRepo.save(any(PdpEarning.class))).thenAnswer(i -> {
            PdpEarning e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            if (!stored.contains(e)) {
                stored.add(e);
            }
            return e;
        });
        when(earningRepo.findBySource(eq(COMPANY), any(), any())).thenAnswer(i -> stored.stream()
                .filter(e -> e.getEarningType() == i.getArgument(1) && e.getSourceId().equals(i.getArgument(2))).findFirst());
        when(statementRepo.save(any(PdpStatement.class))).thenAnswer(i -> {
            PdpStatement s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        Branch partner = Branch.builder().branchCode("PDP-1").branchName("Partner").branchType(BranchType.PICKUP_DELIVERY_PARTNER).build();
        partner.setId(PARTNER);
        when(branchService.getById(PARTNER)).thenReturn(partner);
        when(statementRepo.countCreatedBetween(any(), any(), any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void rate(PdpRateType type, String perKg, String total, String oda) {
        when(rateService.quote(org.mockito.ArgumentMatchers.argThat(q -> q != null && q.rateType() == type)))
                .thenReturn(new Quote(UUID.randomUUID(), type, BigDecimal.TEN, BigDecimal.TEN, new BigDecimal(perKg),
                        new BigDecimal(total), BigDecimal.ZERO, new BigDecimal(oda), new BigDecimal(total).add(new BigDecimal(oda))));
    }

    @Test
    @DisplayName("a pickup earning is priced from the pickup card and snapshotted")
    void pickupPriced() {
        rate(PdpRateType.PICKUP, "4", "40", "0");
        UUID pickup = UUID.randomUUID();

        earningService.recordPickup(PARTNER, pickup, "PU-1", "411001", BigDecimal.TEN, DAY);

        PdpEarning e = stored.get(0);
        assertThat(e.getStatus()).isEqualTo(PdpEarningStatus.ACCRUED);
        assertThat(e.getTotalAmount()).isEqualByComparingTo("40");
        assertThat(e.getRatePerKg()).isEqualByComparingTo("4");
        assertThat(e.getDistrictId()).isEqualTo(DISTRICT);
    }

    @Test
    @DisplayName("a job is never paid twice")
    void idempotent() {
        rate(PdpRateType.DELIVERY, "6", "60", "50");
        UUID shipment = UUID.randomUUID();

        earningService.recordDelivery(PARTNER, shipment, "SH-1", "411001", BigDecimal.TEN, DAY);
        earningService.recordDelivery(PARTNER, shipment, "SH-1", "411001", BigDecimal.TEN, DAY);

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTotalAmount()).isEqualByComparingTo("110");
    }

    @Test
    @DisplayName("no rate card leaves the earning UNRATED at zero; it never throws")
    void unrated() {
        when(rateService.quote(any())).thenThrow(new BusinessRuleException("no card"));

        earningService.recordPickup(PARTNER, UUID.randomUUID(), "PU-2", "411001", BigDecimal.TEN, DAY);

        assertThat(stored.get(0).getStatus()).isEqualTo(PdpEarningStatus.UNRATED);
        assertThat(stored.get(0).getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("re-pricing an unrated earning works once a card exists")
    void repriceWorks() {
        when(rateService.quote(any())).thenThrow(new BusinessRuleException("no card"));
        earningService.recordPickup(PARTNER, UUID.randomUUID(), "PU-3", "411001", BigDecimal.TEN, DAY);
        PdpEarning e = stored.get(0);
        when(earningRepo.findByIdWithinCompany(e.getId(), COMPANY)).thenReturn(Optional.of(e));
        org.mockito.Mockito.reset(rateService);
        rate(PdpRateType.PICKUP, "5", "50", "0");

        // reprice is admin-gated by @PreAuthorize, which a plain unit test does not apply
        PdpEarning repriced = earningService.reprice(e.getId());

        assertThat(repriced.getStatus()).isEqualTo(PdpEarningStatus.ACCRUED);
        assertThat(repriced.getTotalAmount()).isEqualByComparingTo("50");
    }

    private PdpEarning accrued(PdpEarningType type, String amount) {
        PdpEarning e = PdpEarning.builder().branchId(PARTNER).earningType(type).sourceId(UUID.randomUUID())
                .referenceNo("R").earnedOn(DAY).status(PdpEarningStatus.ACCRUED)
                .totalAmount(new BigDecimal(amount)).build();
        e.setId(UUID.randomUUID());
        return e;
    }

    @Test
    @DisplayName("a statement totals pickups and deliveries separately and bills the earnings")
    void generateStatement() {
        List<PdpEarning> lines = List.of(accrued(PdpEarningType.PICKUP, "40"), accrued(PdpEarningType.PICKUP, "30"),
                accrued(PdpEarningType.DELIVERY, "110"));
        when(earningRepo.findBillable(COMPANY, PARTNER, DAY.minusDays(7), DAY)).thenReturn(lines);
        when(earningRepo.findByStatement(eq(COMPANY), any())).thenReturn(lines);

        StatementView v = statementService.generate(PARTNER, DAY.minusDays(7), DAY, null);

        PdpStatement s = v.statement();
        assertThat(s.getPickupTotal()).isEqualByComparingTo("70");
        assertThat(s.getDeliveryTotal()).isEqualByComparingTo("110");
        assertThat(s.getNetPayable()).isEqualByComparingTo("180");
        assertThat(s.getPickupCount()).isEqualTo(2);
        assertThat(s.getStatementNo()).startsWith("PS-").endsWith("-0001");
        assertThat(lines).allMatch(e -> e.getStatus() == PdpEarningStatus.BILLED && s.getId().equals(e.getStatementId()));
    }

    @Test
    @DisplayName("no unbilled earnings means no statement")
    void emptyRejected() {
        when(earningRepo.findBillable(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> statementService.generate(PARTNER, DAY.minusDays(7), DAY, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no unbilled earnings");
    }

    @Test
    @DisplayName("draft -> approved -> paid, in that order only")
    void lifecycle() {
        PdpStatement s = PdpStatement.builder().statementNo("PS-1").branchId(PARTNER).periodFrom(DAY).periodTo(DAY).build();
        s.setId(UUID.randomUUID());
        when(statementRepo.findByIdWithinCompany(s.getId(), COMPANY)).thenReturn(Optional.of(s));
        when(earningRepo.findByStatement(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> statementService.markPaid(s.getId(), DAY, "UTR1")).isInstanceOf(BusinessRuleException.class);
        statementService.approve(s.getId());
        assertThat(s.getStatus()).isEqualTo(PdpStatementStatus.APPROVED);
        statementService.markPaid(s.getId(), DAY, "UTR1");
        assertThat(s.getStatus()).isEqualTo(PdpStatementStatus.PAID);
        assertThat(s.getPaymentRef()).isEqualTo("UTR1");
        assertThatThrownBy(() -> statementService.delete(s.getId())).hasMessageContaining("draft");
    }

    @Test
    @DisplayName("deleting a draft statement releases its earnings")
    void deleteReleases() {
        PdpStatement s = PdpStatement.builder().statementNo("PS-2").branchId(PARTNER).periodFrom(DAY).periodTo(DAY).build();
        s.setId(UUID.randomUUID());
        PdpEarning e = accrued(PdpEarningType.PICKUP, "40");
        e.setStatus(PdpEarningStatus.BILLED);
        e.setStatementId(s.getId());
        when(statementRepo.findByIdWithinCompany(s.getId(), COMPANY)).thenReturn(Optional.of(s));
        when(earningRepo.findByStatement(COMPANY, s.getId())).thenReturn(List.of(e));

        statementService.delete(s.getId());

        assertThat(e.getStatus()).isEqualTo(PdpEarningStatus.ACCRUED);
        assertThat(e.getStatementId()).isNull();
        assertThat(s.isDeleted()).isTrue();
    }

    private void callerAt(UUID userId, UUID branchId, String role) {
        User u = org.mockito.Mockito.mock(User.class);
        when(u.getBranchId()).thenReturn(branchId);
        when(userService.getById(userId)).thenReturn(u);
        AuthenticatedUser p = new AuthenticatedUser(userId, COMPANY, "p@test", java.util.Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    @Test
    @DisplayName("a partner sees only its own approved statements; drafts and other partners' are hidden")
    void partnerScopedStatements() {
        UUID me = UUID.randomUUID();
        callerAt(me, PARTNER, com.courier.shared.security.Roles.BRANCH_MANAGER);
        PdpStatement mine = PdpStatement.builder().statementNo("PS-1").branchId(PARTNER).periodFrom(DAY).periodTo(DAY)
                .status(PdpStatementStatus.APPROVED).build();
        mine.setId(UUID.randomUUID());
        PdpStatement draft = PdpStatement.builder().statementNo("PS-2").branchId(PARTNER).periodFrom(DAY).periodTo(DAY).build();
        draft.setId(UUID.randomUUID());
        PdpStatement theirs = PdpStatement.builder().statementNo("PS-3").branchId(UUID.randomUUID()).periodFrom(DAY).periodTo(DAY)
                .status(PdpStatementStatus.APPROVED).build();
        theirs.setId(UUID.randomUUID());
        for (PdpStatement x : List.of(mine, draft, theirs)) {
            when(statementRepo.findByIdWithinCompany(x.getId(), COMPANY)).thenReturn(Optional.of(x));
        }
        when(earningRepo.findByStatement(any(), any())).thenReturn(List.of());
        when(earningRepo.countUnrated(any(), any(), any(), any())).thenReturn(0L);

        assertThat(statementService.get(mine.getId()).statement()).isSameAs(mine);
        assertThatThrownBy(() -> statementService.get(draft.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> statementService.get(theirs.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelling a shipment: an unbilled earning is reversed, a billed one gets a negative adjustment")
    void reversal() {
        rate(PdpRateType.PICKUP, "4", "40", "0");
        UUID pickup = UUID.randomUUID();
        earningService.recordPickup(PARTNER, pickup, "PU-9", "411001", BigDecimal.TEN, DAY);
        PdpEarning unbilled = stored.get(0);

        earningService.reverseForShipment(UUID.randomUUID(), pickup);
        assertThat(unbilled.getStatus()).isEqualTo(PdpEarningStatus.REVERSED);
        assertThat(stored).hasSize(1); // nothing was billed, so no adjustment

        UUID pickup2 = UUID.randomUUID();
        earningService.recordPickup(PARTNER, pickup2, "PU-10", "411001", BigDecimal.TEN, DAY);
        PdpEarning billed = stored.get(1);
        billed.setStatus(PdpEarningStatus.BILLED);

        earningService.reverseForShipment(UUID.randomUUID(), pickup2);
        earningService.reverseForShipment(UUID.randomUUID(), pickup2); // idempotent

        assertThat(billed.getStatus()).isEqualTo(PdpEarningStatus.REVERSED);
        assertThat(stored).hasSize(3);
        PdpEarning adj = stored.get(2);
        assertThat(adj.getTotalAmount()).isEqualByComparingTo("-40");
        assertThat(adj.getStatus()).isEqualTo(PdpEarningStatus.ACCRUED);
        assertThat(adj.getReferenceNo()).isEqualTo("REV-PU-10");
    }
}
