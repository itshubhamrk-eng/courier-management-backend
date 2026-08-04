package com.courier;

import com.courier.modules.auth.application.AuthProperties;
import com.courier.modules.finance.infrastructure.RazorpayProperties;
import com.courier.modules.pricing.application.PricingProperties;
import com.courier.shared.config.CorsProperties;
import com.courier.shared.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Multi-company Courier SaaS backend.
 *
 * <p>Read {@code MEMORY/AI_CONTEXT.md} before changing anything here.
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AuthProperties.class,
        RazorpayProperties.class, PricingProperties.class})
public class CourierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApplication.class, args);
    }
}
