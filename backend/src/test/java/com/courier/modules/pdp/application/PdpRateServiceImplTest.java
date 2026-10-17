package com.courier.modules.pdp.application;

import com.courier.modules.company.application.BranchService;
import com.courier.modules.company.application.UserService;
import com.courier.shared.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchType;
import com.courier.modules.pdp.application.PdpRateService.Quote;
import com.courier.modules.pdp.application.PdpRateService.QuoteCommand;
import com.courier.modules.pdp.application.PdpRateService.SaveCommand;
import com.courier.modules.pdp.application.PdpRateService.SlabInput;
import com.courier.modules.pdp.domain.PdpRateCard;
import com.courier.modules.pdp.domain.PdpRateCardRepository;
import com.courier.modules.pdp.domain.PdpRateSlab;
import com.courier.modules.pdp.domain.PdpRateSlabRepository;
import com.courier.modules.pdp.domain.PdpRateType;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdpRateServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();
    private static final UUID DISTRICT = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @Mock private PdpRateCardRepository rates;
    @Mock private PdpRateSlabRepository slabs;
    @Mock private BranchService branchService;
    @Mock private AuditService auditService;
    @Mock private UserService userService;

    private PdpRateServiceImpl service;
    private final List<PdpRateCard> stored = new ArrayList<>();
    private final List<PdpRateSlab> storedSlabs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PdpRateServiceImpl(rates, slabs, branchService, auditService, userService);
        CompanyContext.setCompanyId(COMPANY);
        Branch partner = Branch.builder().branchCode("PDP-1").branchName("Partner One")
                .branchType(BranchType.PICKUP_DELIVERY_PARTNER).build();
        partner.setId(PARTNER);
        when(branchService.getById(PARTNER)).thenReturn(partner);
        when(rates.save(any(PdpRateCard.class))).thenAnswer(i -> {
            PdpRateCard c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            if (!stored.contains(c)) {
                stored.add(c);
            }
            return c;
        });
        when(rates.findByBranchAndType(eq(COMPANY), eq(PARTNER), any())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getRateType() == i.getArgument(2)).toList());
        when(slabs.save(any(PdpRateSlab.class))).thenAnswer(i -> {
            PdpRateSlab s = i.getArgument(0);
            storedSlabs.add(s);
            return s;
        });
        when(slabs.findByRate(eq(COMPANY), any())).thenAnswer(i -> storedSlabs.stream()
                .filter(s -> s.getRateId().equals(i.getArgument(1))).toList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private static SaveCommand cmd(PdpRateType type, UUID district, String min, List<SlabInput> slabList) {
        return new SaveCommand(PARTNER, type, district, new BigDecimal(min), null, null, null, null,
                null, null, slabList, null);
    }

    private static SlabInput slab(String from, String to, String rate) {
        return new SlabInput(new BigDecimal(from), to == null ? null : new BigDecimal(to), new BigDecimal(rate));
    }

    @Test
    @DisplayName("pickup charge = max(actual, minimum pickup weight) x slab rate")
    void minimumWeightApplies() {
        service.create(cmd(PdpRateType.PICKUP, null, "10",
                List.of(slab("0", "50", "4"), slab("50", null, "3"))));

        Quote q = service.quote(new QuoteCommand(PARTNER, PdpRateType.PICKUP, DISTRICT,
                new BigDecimal("6"), false, DAY));

        assertThat(q.chargeableWeight()).isEqualByComparingTo("10");
        assertThat(q.ratePerKg()).isEqualByComparingTo("4");
        assertThat(q.total()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("heavier shipment falls in the next slab")
    void slabSelection() {
        service.create(cmd(PdpRateType.DELIVERY, null, "5",
                List.of(slab("0", "50", "4"), slab("50", null, "3"))));

        Quote q = service.quote(new QuoteCommand(PARTNER, PdpRateType.DELIVERY, DISTRICT,
                new BigDecimal("80"), false, DAY));

        assertThat(q.ratePerKg()).isEqualByComparingTo("3");
        assertThat(q.total()).isEqualByComparingTo("240.00");
    }

    @Test
    @DisplayName("district card wins over the all-districts card")
    void districtCardWins() {
        service.create(cmd(PdpRateType.DELIVERY, null, "0", List.of(slab("0", null, "5"))));
        service.create(cmd(PdpRateType.DELIVERY, DISTRICT, "0", List.of(slab("0", null, "2"))));

        Quote q = service.quote(new QuoteCommand(PARTNER, PdpRateType.DELIVERY, DISTRICT,
                new BigDecimal("10"), false, DAY));

        assertThat(q.ratePerKg()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("pickup and delivery rates are independent")
    void typesIndependent() {
        service.create(cmd(PdpRateType.PICKUP, null, "0", List.of(slab("0", null, "3"))));
        service.create(cmd(PdpRateType.DELIVERY, null, "0", List.of(slab("0", null, "7"))));

        assertThat(service.quote(new QuoteCommand(PARTNER, PdpRateType.PICKUP, DISTRICT,
                BigDecimal.TEN, false, DAY)).total()).isEqualByComparingTo("30.00");
        assertThat(service.quote(new QuoteCommand(PARTNER, PdpRateType.DELIVERY, DISTRICT,
                BigDecimal.TEN, false, DAY)).total()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("no live card means a clear error, never a silent fallback")
    void noCard() {
        assertThatThrownBy(() -> service.quote(new QuoteCommand(PARTNER, PdpRateType.PICKUP, DISTRICT,
                BigDecimal.TEN, false, DAY)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no active pickup rate");
    }

    @Test
    @DisplayName("overlapping slabs are rejected")
    void overlappingSlabs() {
        assertThatThrownBy(() -> service.create(cmd(PdpRateType.PICKUP, null, "0",
                List.of(slab("0", "50", "4"), slab("40", null, "3")))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("overlap");
    }

    @Test
    @DisplayName("only the last slab may be open-ended")
    void openEndedMustBeLast() {
        assertThatThrownBy(() -> service.create(cmd(PdpRateType.PICKUP, null, "0",
                List.of(slab("0", null, "4"), slab("50", "100", "3")))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("last weight slab");
    }

    @Test
    @DisplayName("two active cards for the same partner, type and district in one period are rejected")
    void duplicateActiveCard() {
        service.create(cmd(PdpRateType.PICKUP, DISTRICT, "0", List.of(slab("0", null, "4"))));

        assertThatThrownBy(() -> service.create(cmd(PdpRateType.PICKUP, DISTRICT, "0", List.of(slab("0", null, "5")))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already covers");
    }

    @Test
    @DisplayName("a branch that is not a PDP cannot hold partner rates")
    void notAPartner() {
        UUID plain = UUID.randomUUID();
        Branch b = Branch.builder().branchCode("BR-1").branchName("Plain").branchType(BranchType.BRANCH).build();
        b.setId(plain);
        when(branchService.getById(plain)).thenReturn(b);

        assertThatThrownBy(() -> service.create(new SaveCommand(plain, PdpRateType.PICKUP, null, BigDecimal.ZERO,
                null, null, null, null, null, null, List.of(slab("0", null, "4")), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not a Pickup-Delivery Partner");
    }

    @Test
    @DisplayName("mathadi / ODA / return fee are delivery-only")
    void pickupRejectsDeliveryExtras() {
        assertThatThrownBy(() -> service.create(new SaveCommand(PARTNER, PdpRateType.PICKUP, null, BigDecimal.ZERO,
                new BigDecimal("5"), null, null, null, null, null, List.of(slab("0", null, "4")), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("delivery rates only");
    }

    @Test
    @DisplayName("a partner can only price and read its own rate cards")
    void partnerScopedReads() {
        service.create(cmd(PdpRateType.PICKUP, null, "0", List.of(slab("0", null, "4"))));
        UUID me = UUID.randomUUID();
        com.courier.modules.company.domain.User u = org.mockito.Mockito.mock(com.courier.modules.company.domain.User.class);
        when(u.getBranchId()).thenReturn(UUID.randomUUID()); // a different partner
        when(userService.getCurrent()).thenReturn(u);
        AuthenticatedUser p = new AuthenticatedUser(me, COMPANY, "p@test", java.util.Set.of("BRANCH_MANAGER"), "jti");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.authorities()));

        assertThatThrownBy(() -> service.calculate(new QuoteCommand(PARTNER, PdpRateType.PICKUP, DISTRICT,
                BigDecimal.TEN, false, DAY))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("your own");
        assertThat(service.search(null, null)).isEmpty();
    }
}
