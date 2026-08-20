package com.courier.modules.finance.infrastructure;

import com.courier.modules.finance.application.payment.PaymentGatewayPort;
import com.courier.modules.finance.domain.CompanyRazorpayConfig;
import com.courier.modules.finance.domain.CompanyRazorpayConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Picks which {@link PaymentGatewayPort} a wallet recharge uses: the company's own Razorpay
 * account if it has configured and enabled one, otherwise the platform-wide gateway
 * ({@link PaymentGatewayConfig}'s bean — Razorpay-from-env, or
 * {@link UnconfiguredPaymentGateway} if that isn't set either).
 *
 * <p>This is additive, not a replacement: a deployment where no company has its own
 * Razorpay account behaves exactly as before. {@link RazorpayPaymentGateway} and
 * {@link RazorpayProperties} are reused unchanged — building a company-scoped instance is
 * just constructing them with that company's key id/secret instead of the env-configured
 * ones, which is the entire point of {@code PaymentGatewayPort} already being
 * gateway-agnostic.
 */
@Component
@RequiredArgsConstructor
public class CompanyPaymentGatewayResolver {

    private final CompanyRazorpayConfigRepository repository;
    private final PaymentGatewayPort platformDefaultGateway;
    private final RestClient.Builder restClientBuilder;

    public PaymentGatewayPort resolve(UUID companyId) {
        return repository.findByCompanyId(companyId)
                .filter(CompanyRazorpayConfig::hasCredentials)
                .<PaymentGatewayPort>map(this::toGateway)
                .orElse(platformDefaultGateway);
    }

    private PaymentGatewayPort toGateway(CompanyRazorpayConfig config) {
        RazorpayProperties properties = new RazorpayProperties();
        properties.setEnabled(true);
        properties.setKeyId(config.getKeyId());
        properties.setKeySecret(config.getKeySecret());
        // apiBaseUrl, merchantName and the timeouts keep RazorpayProperties' own defaults —
        // a company brings credentials, not a different Razorpay API endpoint.
        return new RazorpayPaymentGateway(properties, restClientBuilder);
    }
}
