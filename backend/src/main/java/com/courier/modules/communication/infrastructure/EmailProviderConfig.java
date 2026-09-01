package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.EmailProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Same two-explicit-conditions shape as {@code WhatsAppProviderConfig}/{@code
 * PaymentGatewayConfig}. Enabling SMTP without {@code spring.mail.host} configured fails at
 * startup, not at the first send — the same "a misconfigured gateway is a deploy failure"
 * rule {@code PaymentGatewayConfig} states for Razorpay ({@code JavaMailSender} is only
 * auto-configured by Spring Boot when {@code spring.mail.host} is set, so its absence here
 * means exactly that).
 */
@Slf4j
@Configuration
public class EmailProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.email", name = "enabled", havingValue = "true")
    public EmailProvider smtpEmailProvider(ObjectProvider<JavaMailSender> mailSender) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException(
                    "app.communication.email.enabled is true but no JavaMailSender is configured. "
                            + "Set spring.mail.host (and username/password if the relay needs auth), "
                            + "or disable email.");
        }
        log.info("Email provider: SMTP");
        return new SmtpEmailProvider(sender);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.email", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public EmailProvider logOnlyEmailProvider() {
        log.warn("Email provider: log-only — set app.communication.email.enabled=true "
                + "and spring.mail.host for real SMTP sends.");
        return new LogOnlyEmailProvider();
    }
}
