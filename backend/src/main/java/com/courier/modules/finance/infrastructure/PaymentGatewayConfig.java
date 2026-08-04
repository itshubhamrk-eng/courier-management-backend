package com.courier.modules.finance.infrastructure;

import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the payment gateway for this deployment.
 *
 * <p>The two conditions are mutually exclusive and both explicit — no
 * {@code @ConditionalOnMissingBean}. Condition evaluation order between a scanned component
 * and a conditional {@code @Bean} is not something to leave to chance when the failure mode
 * is "no gateway bean, context won't start" or, worse, "the wrong gateway silently wins".
 * The same reasoning is written on {@code CompanyDirectory}.
 *
 * <p>Enabling Razorpay without credentials fails at startup rather than at the first
 * recharge: a misconfigured gateway should be a deploy failure, not a customer's problem.
 */
@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.payment.razorpay", name = "enabled", havingValue = "true")
    public PaymentGatewayPort razorpayPaymentGateway(RazorpayProperties properties,
                                                     RestClient.Builder restClientBuilder) {
        if (isBlank(properties.getKeyId()) || isBlank(properties.getKeySecret())) {
            throw new IllegalStateException(
                    "app.payment.razorpay.enabled is true but key-id/key-secret are not set. "
                            + "Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET, or disable the gateway.");
        }
        log.info("Razorpay payment gateway enabled (key id {}…)",
                properties.getKeyId().substring(0, Math.min(8, properties.getKeyId().length())));
        return new RazorpayPaymentGateway(properties, restClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.payment.razorpay", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public PaymentGatewayPort unconfiguredPaymentGateway() {
        log.warn("No payment gateway configured — online wallet recharge will be refused. "
                + "Manual credit by a company admin still works.");
        return new UnconfiguredPaymentGateway();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
