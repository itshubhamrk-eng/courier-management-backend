package com.courier.modules.finance.infrastructure;

import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.shared.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The gateway used when none is configured. Refuses every online recharge.
 *
 * <p>Fails closed on purpose. The tempting alternative — accept the recharge and skip
 * signature verification when there is no secret — is a switch that credits a wallet for a
 * payment nobody made, and it is exactly the sort of "dev only" flag that reaches
 * production. A deployment without gateway credentials simply has no online recharge; a
 * company admin can still credit a branch manually, which is an audited action by a
 * named human.
 */
@Slf4j
public class UnconfiguredPaymentGateway implements PaymentGatewayPort {

    private static final String MESSAGE =
            "Online recharge is not available: no payment gateway is configured for this "
                    + "deployment. A company administrator can credit the wallet manually.";

    @Override
    public String name() {
        return "NONE";
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt,
                                    Map<String, String> notes) {
        log.warn("Recharge order refused: no payment gateway configured (receipt {})", receipt);
        throw new BusinessRuleException(MESSAGE);
    }

    @Override
    public void verifyPayment(PaymentConfirmation confirmation) {
        log.warn("Payment verification refused: no payment gateway configured");
        throw new BusinessRuleException(MESSAGE);
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        log.warn("Payment lookup refused: no payment gateway configured");
        throw new BusinessRuleException(MESSAGE);
    }
}
