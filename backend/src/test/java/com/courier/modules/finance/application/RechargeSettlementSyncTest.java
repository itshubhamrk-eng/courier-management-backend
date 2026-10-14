package com.courier.modules.finance.application;

import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.PaymentStatus;
import com.courier.modules.finance.domain.RechargeMode;
import com.courier.modules.finance.domain.RechargeOrder;
import com.courier.modules.finance.domain.RechargeOrderRepository;
import com.courier.modules.finance.infrastructure.CompanyPaymentGatewayResolver;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RechargeSettlementSyncTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private RechargeOrderRepository repository;
    @Mock private CompanyPaymentGatewayResolver resolver;
    @Mock private PaymentGatewayPort gateway;

    private RechargeSettlementSync sync;

    @BeforeEach
    void setUp() {
        sync = new RechargeSettlementSync(repository, resolver);
        when(resolver.resolve(COMPANY)).thenReturn(gateway);
    }

    private RechargeOrder credited(String paymentId) {
        RechargeOrder o = RechargeOrder.builder()
                .walletId(UUID.randomUUID()).branchId(UUID.randomUUID()).transactionNo("WRC-1")
                .amount(BigDecimal.valueOf(5000)).currency("INR").mode(RechargeMode.ONLINE).gateway("RAZORPAY")
                .gatewayPaymentId(paymentId).status(PaymentStatus.SUCCESS)
                .settledAt(Instant.now().minusSeconds(86400 * 3L)).build();
        o.setCompanyId(COMPANY);
        return o;
    }

    @Test
    void stampsSettlementWhenThePaymentWasPaidOut() {
        RechargeOrder paid = credited("pay_1");
        RechargeOrder notYet = credited("pay_2");
        when(repository.findAllById(any())).thenReturn(List.of(paid, notYet));
        Instant at = Instant.parse("2026-10-12T05:00:00Z");
        when(gateway.fetchSettlements(any(), any())).thenReturn(
                List.of(new PaymentGatewayPort.PaymentSettlement("pay_1", "setl_9", "UTR123", at)));

        int settled = sync.sync(COMPANY, List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(settled).isEqualTo(1);
        assertThat(paid.isBankSettled()).isTrue();
        assertThat(paid.getBankSettlementId()).isEqualTo("setl_9");
        assertThat(paid.getBankSettlementUtr()).isEqualTo("UTR123");
        assertThat(paid.getBankSettledAt()).isEqualTo(at);
        assertThat(notYet.isBankSettled()).isFalse();
        assertThat(notYet.getSettlementLastCheckedAt()).isNotNull();
        verify(repository).saveAll(any());
    }

    @Test
    void gatewayFailureLeavesEverythingUntouched() {
        RechargeOrder o = credited("pay_1");
        when(repository.findAllById(any())).thenReturn(List.of(o));
        when(gateway.fetchSettlements(any(), any())).thenThrow(new BusinessRuleException("down"));

        assertThat(sync.sync(COMPANY, List.of(UUID.randomUUID()))).isZero();
        assertThat(o.isBankSettled()).isFalse();
    }

    @Test
    void otherCompaniesRechargesAreIgnored() {
        RechargeOrder foreign = credited("pay_1");
        foreign.setCompanyId(UUID.randomUUID());
        when(repository.findAllById(any())).thenReturn(List.of(foreign));

        assertThat(sync.sync(COMPANY, List.of(UUID.randomUUID()))).isZero();
    }
}
