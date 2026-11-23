package com.courier.modules.finance.application;

import com.courier.modules.finance.application.WalletService.PeriodSummary;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.PendingCommissionPort;
import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.RechargeOrderRepository;
import com.courier.modules.finance.domain.SubTransactionType;
import com.courier.modules.finance.domain.TransactionType;
import com.courier.modules.finance.domain.Wallet;
import com.courier.modules.finance.domain.WalletRepository;
import com.courier.modules.finance.domain.WalletStatus;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.finance.domain.WalletTransactionRepository;
import com.courier.modules.finance.infrastructure.CompanyPaymentGatewayResolver;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Period figures for a branch statement. The repository is mocked with answers that honour
 * the documented window semantics over an in-memory ledger, so what is asserted is the
 * service's arithmetic and window handling (the JPQL itself is not exercised here).
 *
 * <p>A pending gateway recharge never reaches the ledger (it lives in RechargeOrder until
 * settled), so there is no ledger row to exclude for it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletPeriodSummaryTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-01T00:00:00Z");

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private BranchDirectoryPort branchDirectory;
    @Mock private PendingCommissionPort pendingCommission;
    @Mock private CompanyPaymentGatewayResolver paymentGatewayResolver;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RechargeOrderTracker rechargeOrders;
    @Mock private RechargeOrderRepository rechargeOrderRepository;

    private WalletServiceImpl service;
    private Wallet wallet;
    private final List<WalletTransaction> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(walletRepository, transactionRepository, branchDirectory,
                pendingCommission, paymentGatewayResolver, auditService, eventPublisher,
                rechargeOrders, rechargeOrderRepository);
        wallet = Wallet.builder().walletNumber("WLT1").branchId(BRANCH).status(WalletStatus.ACTIVE)
                .availableBalance(Wallet.ZERO).holdBalance(Wallet.ZERO).currency("INR").build();
        wallet.setCompanyId(COMPANY);
        when(walletRepository.findByBranchIdWithinCompany(BRANCH, COMPANY)).thenReturn(Optional.of(wallet));
        when(branchDirectory.findBranch(BRANCH, COMPANY)).thenReturn(Optional.of(
                new BranchDirectoryPort.BranchRef(BRANCH, COMPANY, "PUNE", "Pune", true)));

        when(transactionRepository.findBetween(any(), eq(COMPANY), any(), any()))
                .thenAnswer(i -> ledger.stream()
                        .filter(t -> !t.getCreatedAt().isBefore(i.<Instant>getArgument(2))
                                && t.getCreatedAt().isBefore(i.<Instant>getArgument(3)))
                        .sorted(Comparator.comparing(WalletTransaction::getCreatedAt)).toList());
        when(transactionRepository.findLatestBefore(any(), eq(COMPANY), any(), any(Pageable.class)))
                .thenAnswer(i -> ledger.stream()
                        .filter(t -> t.getCreatedAt().isBefore(i.<Instant>getArgument(2)))
                        .sorted(Comparator.comparing(WalletTransaction::getCreatedAt).reversed())
                        .limit(1).toList());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void add(Instant at, TransactionType type, SubTransactionType sub, String amount,
                     String before, String after) {
        WalletTransaction t = WalletTransaction.builder().transactionNo("T" + ledger.size())
                .walletId(wallet.getId()).transactionType(type).subTransactionType(sub)
                .amount(new BigDecimal(amount)).balanceBefore(new BigDecimal(before))
                .balanceAfter(new BigDecimal(after)).referenceType(ReferenceType.SHIPMENT).build();
        t.setCompanyId(COMPANY);
        t.setCreatedAt(at);
        ledger.add(t);
    }

    private void fixedLedger() {
        add(FROM.minusMillis(1), TransactionType.CR, SubTransactionType.MCR, "200", "0", "200");
        add(FROM, TransactionType.CR, SubTransactionType.WRC, "1000", "200", "1200");
        add(FROM.plusSeconds(60), TransactionType.DR, SubTransactionType.SBK, "300", "1200", "900");
        add(FROM.plusSeconds(120), TransactionType.CR, SubTransactionType.COM, "50", "900", "950");
        add(FROM.plusSeconds(180), TransactionType.DR, SubTransactionType.COM, "10", "950", "940");
        add(FROM.plusSeconds(240), TransactionType.CR, SubTransactionType.DRS, "20", "940", "960");
        // exactly at the exclusive upper bound: belongs to the next period
        add(TO, TransactionType.CR, SubTransactionType.WRC, "700", "960", "1660");
    }

    @Test
    void summarisesWindowFromTheLedger() {
        fixedLedger();
        PeriodSummary s = service.periodSummaryUnguarded(COMPANY, BRANCH, FROM, TO);

        assertThat(s.openingBalance()).isEqualByComparingTo("200");
        assertThat(s.closingBalance()).isEqualByComparingTo("960");
        assertThat(s.rechargeCount()).isEqualTo(1);
        assertThat(s.rechargeAmount()).isEqualByComparingTo("1000");
        assertThat(s.commissionCredited()).isEqualByComparingTo("50");
        assertThat(s.commissionDebited()).isEqualByComparingTo("10");
        assertThat(s.drsChargeCredited()).isEqualByComparingTo("20");
        assertThat(s.bookingDebited()).isEqualByComparingTo("300");
    }

    @Test
    void branchWithoutWalletReturnsZeros() {
        UUID other = UUID.randomUUID();
        when(walletRepository.findByBranchIdWithinCompany(other, COMPANY)).thenReturn(Optional.empty());

        PeriodSummary s = service.periodSummaryUnguarded(COMPANY, other, FROM, TO);

        assertThat(s.openingBalance()).isEqualByComparingTo("0");
        assertThat(s.closingBalance()).isEqualByComparingTo("0");
        assertThat(s.rechargeCount()).isZero();
        assertThat(s.rechargeAmount()).isEqualByComparingTo("0");
        assertThat(s.commissionCredited()).isEqualByComparingTo("0");
        assertThat(s.commissionDebited()).isEqualByComparingTo("0");
        assertThat(s.drsChargeCredited()).isEqualByComparingTo("0");
        assertThat(s.bookingDebited()).isEqualByComparingTo("0");
        assertThat(service.transactionsBetween(COMPANY, other, FROM, TO)).isEmpty();
    }

    @Test
    void emptyWindowKeepsLastKnownBalanceAsOpeningAndClosing() {
        add(FROM.minusSeconds(5), TransactionType.CR, SubTransactionType.WRC, "400", "0", "400");

        PeriodSummary s = service.periodSummaryUnguarded(COMPANY, BRANCH, FROM, TO);

        assertThat(s.openingBalance()).isEqualByComparingTo("400");
        assertThat(s.closingBalance()).isEqualByComparingTo("400");
        assertThat(s.rechargeCount()).isZero();
    }

    @Test
    void transactionsBetweenIsAscendingAndHalfOpen() {
        fixedLedger();
        List<WalletTransaction> rows = service.transactionsBetween(COMPANY, BRANCH, FROM, TO);

        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).getCreatedAt()).isEqualTo(FROM);
        assertThat(rows).extracting(WalletTransaction::getCreatedAt).isSorted();
        assertThat(rows).noneMatch(t -> t.getCreatedAt().equals(TO));
    }

    @Test
    void guardedVariantAppliesBranchAccessRule() {
        fixedLedger();
        CompanyContext.setCompanyId(COMPANY);
        plant(UUID.randomUUID(), Roles.COMPANY_ADMIN);
        assertThat(service.periodSummary(BRANCH, FROM, TO).rechargeAmount()).isEqualByComparingTo("1000");

        // a branch user naming someone else's branch is refused
        UUID uid = UUID.randomUUID();
        when(branchDirectory.branchOfUser(uid, COMPANY)).thenReturn(Optional.of(UUID.randomUUID()));
        plant(uid, Roles.OPERATOR);
        assertThatThrownBy(() -> service.periodSummary(BRANCH, FROM, TO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void plant(UUID userId, String role) {
        AuthenticatedUser p = new AuthenticatedUser(userId, COMPANY, "u@x.test", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }
}
