package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.SmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Same two-explicit-conditions shape as {@code WhatsAppProviderConfig}/{@code
 *  PaymentGatewayConfig}. Per-company API URL/key/sender id are validated per-send, not
 *  here — this only picks whether real HTTP calls are made in this deployment at all. */
@Slf4j
@Configuration
public class SmsProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.sms", name = "enabled", havingValue = "true")
    public SmsProvider genericHttpSmsProvider(RestClient.Builder builder) {
        log.info("SMS provider: generic HTTP gateway (per-company URL/key)");
        return new GenericHttpSmsProvider(builder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.sms", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public SmsProvider logOnlySmsProvider() {
        log.warn("SMS provider: log-only — set app.communication.sms.enabled=true "
                + "for real gateway sends.");
        return new LogOnlySmsProvider();
    }
}
