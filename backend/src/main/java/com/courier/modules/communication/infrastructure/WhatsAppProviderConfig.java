package com.courier.modules.communication.infrastructure;

import com.courier.modules.communication.application.provider.WhatsAppProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the WhatsApp implementation for this deployment. Two explicit, mutually exclusive
 * conditions — no {@code @ConditionalOnMissingBean} — the same discipline {@code
 * PaymentGatewayConfig} documents for why: bean-selection order between a scanned
 * component and a conditional {@code @Bean} is not something to leave to chance.
 *
 * <p>Unlike Razorpay's single platform-wide key pair, WhatsApp credentials are per-company
 * (see {@code CommunicationSetting}), so this only selects the <em>implementation</em> —
 * whether real Meta Cloud API calls are made at all in this deployment — never a specific
 * company's own Phone Number ID/Access Token, which is validated per-send instead.
 */
@Slf4j
@Configuration
public class WhatsAppProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.whatsapp", name = "enabled", havingValue = "true")
    public WhatsAppProvider metaWhatsAppProvider(RestClient.Builder builder) {
        log.info("WhatsApp provider: Meta Cloud API (per-company credentials)");
        return new MetaWhatsAppProvider(builder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.communication.whatsapp", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public WhatsAppProvider logOnlyWhatsAppProvider() {
        log.warn("WhatsApp provider: log-only — set app.communication.whatsapp.enabled=true "
                + "for real Meta Cloud API sends.");
        return new LogOnlyWhatsAppProvider();
    }
}
