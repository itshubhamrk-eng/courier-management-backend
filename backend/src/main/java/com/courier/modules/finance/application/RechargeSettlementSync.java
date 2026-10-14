package com.courier.modules.finance.application;

import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.RechargeOrder;
import com.courier.modules.finance.domain.RechargeOrderRepository;
import com.courier.modules.finance.infrastructure.CompanyPaymentGatewayResolver;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds out whether the money behind a credited online recharge has reached the company's
 * bank. The wallet credit happens at capture; Razorpay pays out later in batches
 * (settlements), and this stamps each recharge with the settlement id, UTR and payout time
 * once its payment appears in one.
 *
 * <p>Uses the company's own Razorpay account (via {@link CompanyPaymentGatewayResolver}) and
 * must run inside that company's {@code CompanyContext}. Read-only against the gateway and
 * never touches the wallet or ledger — settlement is reporting, not accounting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeSettlementSync {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Payouts are daily; anything credited longer ago than this is looked up on demand only up to this window. */
    static final int MAX_LOOKBACK_DAYS = 45;

    private final RechargeOrderRepository repository;
    private final CompanyPaymentGatewayResolver gatewayResolver;

    /** @return how many recharges were newly marked as settled */
    @Transactional
    public int sync(UUID companyId, List<UUID> rechargeIds) {
        List<RechargeOrder> orders = repository.findAllById(rechargeIds).stream()
                .filter(o -> companyId.equals(o.getCompanyId()))
                .filter(o -> o.getGatewayPaymentId() != null && !o.isBankSettled() && o.getSettledAt() != null)
                .toList();
        if (orders.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now(IST);
        LocalDate earliest = orders.stream().map(o -> o.getSettledAt().atZone(IST).toLocalDate())
                .min(LocalDate::compareTo).orElse(today);
        LocalDate from = earliest.isBefore(today.minusDays(MAX_LOOKBACK_DAYS)) ? today.minusDays(MAX_LOOKBACK_DAYS) : earliest;

        Instant now = Instant.now();
        Map<String, PaymentGatewayPort.PaymentSettlement> byPayment;
        try {
            byPayment = new HashMap<>(gatewayResolver.resolve(companyId).fetchSettlements(from, today).stream()
                    .collect(Collectors.toMap(PaymentGatewayPort.PaymentSettlement::paymentId,
                            Function.identity(), (a, b) -> a)));
        } catch (BusinessRuleException unreachable) {
            log.warn("Settlement lookup failed for company {}: {}", companyId, unreachable.getMessage());
            return 0;
        }

        int settled = 0;
        for (RechargeOrder order : orders) {
            order.setSettlementLastCheckedAt(now);
            PaymentGatewayPort.PaymentSettlement s = byPayment.get(order.getGatewayPaymentId());
            if (s != null) {
                order.setBankSettlementId(s.settlementId());
                order.setBankSettlementUtr(s.utr());
                order.setBankSettledAt(s.settledAt());
                settled++;
            }
        }
        repository.saveAll(orders);
        return settled;
    }
}
