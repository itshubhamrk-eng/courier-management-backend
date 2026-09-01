package com.courier;

import com.courier.modules.auth.application.AuthProperties;
import com.courier.modules.communication.application.CommunicationRetryProperties;
import com.courier.modules.company.infrastructure.GeocodingProperties;
import com.courier.modules.distance.infrastructure.RoutingProperties;
import com.courier.modules.finance.infrastructure.RazorpayProperties;
import com.courier.modules.pod.application.PodVerificationProperties;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.modules.shipment.infrastructure.S3Properties;
import com.courier.shared.config.CorsProperties;
import com.courier.shared.security.JwtProperties;
import com.courier.shared.security.SecretsEncryptionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Multi-company Courier SaaS backend.
 *
 * <p>Read {@code MEMORY/AI_CONTEXT.md} before changing anything here.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AuthProperties.class,
        RazorpayProperties.class, PricingProperties.class, S3Properties.class,
        GeocodingProperties.class, RoutingProperties.class, SecretsEncryptionProperties.class,
        PodVerificationProperties.class, CommunicationRetryProperties.class})
public class CourierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApplication.class, args);
    }
}
