package com.courier.modules.branchreport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.BranchDirectoryPort.BranchRef;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.VendorAuditRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchStatementServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private final ShipmentService shipments = mock(ShipmentService.class);
    private final WalletService wallets = mock(WalletService.class);
    private final BranchDirectoryPort branches = mock(BranchDirectoryPort.class);
    private final BranchStatementServiceImpl service =
            new BranchStatementServiceImpl(shipments, wallets, branches);

    private static WalletService.PeriodSummary zeroWallet() {
        BigDecimal z = BigDecimal.ZERO;
        return new WalletService.PeriodSummary(z, z, 0, z, z, z, z, z);
    }

    private static BranchRef ref(UUID id, String code) {
        return new BranchRef(id, COMPANY, code, "Name " + code, true);
    }

    private void stubWallet() {
        when(wallets.periodSummaryUnguarded(any(), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(zeroWallet());
        when(wallets.transactionsBetween(any(), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
    }

    @Test
    void branchWithNothingGetsZeroStatement() {
        UUID b = UUID.randomUUID();
        when(branches.findBranch(b, COMPANY)).thenReturn(Optional.of(ref(b, "B1")));
        when(shipments.vendorAuditUnguarded(any())).thenReturn(List.of());
        stubWallet();

        BranchStatement s = service.build(COMPANY, b, FROM, TO).orElseThrow();
        assertThat(s.branchCode()).isEqualTo("B1");
        assertThat(s.audit().totalBookedOrderCount()).isZero();
        assertThat(s.audit().paidOrderAmount()).isEqualByComparingTo("0");
        assertThat(s.wallet().closingBalance()).isEqualByComparingTo("0");
        assertThat(s.transactions()).isEmpty();
    }

    @Test
    void buildAllCallsAuditOnceAndCoversEveryActiveBranch() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(branches.listBranches(COMPANY)).thenReturn(List.of(ref(a, "A"), ref(b, "B")));
        BigDecimal z = BigDecimal.ZERO;
        VendorAuditRow rowA = new VendorAuditRow(a, 1, 1, z, 0, 0, z, z, z, 4, 0, z, z, z, z, 0,
                z, z, 0L, z, z);
        when(shipments.vendorAuditUnguarded(any())).thenReturn(List.of(rowA));
        stubWallet();

        List<BranchStatement> all = service.buildAll(COMPANY, FROM, TO);

        assertThat(all).extracting(BranchStatement::branchId).containsExactlyInAnyOrder(a, b);
        assertThat(all.stream().filter(s -> s.branchId().equals(a)).findFirst().orElseThrow()
                .audit().totalBookedOrderCount()).isEqualTo(4);
        assertThat(all.stream().filter(s -> s.branchId().equals(b)).findFirst().orElseThrow()
                .audit().totalBookedOrderCount()).isZero();
        verify(shipments, times(1)).vendorAuditUnguarded(any(ShipmentCriteria.class));
    }

    @Test
    void foreignBranchNeverAppears() {
        UUID foreign = UUID.randomUUID();
        when(branches.findBranch(foreign, COMPANY)).thenReturn(Optional.empty());
        when(branches.listBranches(COMPANY)).thenReturn(List.of());
        when(shipments.vendorAuditUnguarded(any())).thenReturn(List.of());

        assertThat(service.build(COMPANY, foreign, FROM, TO)).isEmpty();
        assertThat(service.buildAll(COMPANY, FROM, TO)).isEmpty();
    }
}
