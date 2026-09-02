package com.courier.modules.master.infrastructure;

import com.courier.modules.master.application.port.PincodePostalLookupProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Picks the pincode postal-lookup implementation. Enabled by default — unlike every other
 * provider config in this codebase, the India Post directory needs no vendor credential,
 * so there is nothing to be missing in a fresh environment. {@code
 * app.master.pincode-lookup.enabled=false} opts a deployment out (offline dev box, a
 * network policy that blocks the call) without touching {@code PincodeServiceImpl}.
 */
@Slf4j
@Configuration
public class PincodeLookupConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.master.pincode-lookup", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public PincodePostalLookupProvider indiaPostPincodeLookupProvider(RestClient.Builder builder) {
        log.info("Pincode postal lookup: India Post public directory (api.postalpincode.in)");
        return new IndiaPostPincodeLookupProvider(builder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.master.pincode-lookup", name = "enabled", havingValue = "false")
    public PincodePostalLookupProvider disabledPincodePostalLookupProvider() {
        log.warn("Pincode postal lookup: disabled — Area must be picked manually on create.");
        return new DisabledPincodePostalLookupProvider();
    }
}
