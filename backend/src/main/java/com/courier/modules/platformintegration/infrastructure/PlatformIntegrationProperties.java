package com.courier.modules.platformintegration.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for being a product of the OCCPL SaaS platform. Everything is off unless
 * {@code occpl.platform.enabled=true}, so a deployment that is not connected to the
 * platform behaves exactly as before.
 *
 * @param enabled               turns the provisioning endpoint and its security chain on
 * @param issuerUri             Keycloak realm issuer; tokens must carry this {@code iss}
 * @param jwkSetUri             where the realm's signing keys are fetched from
 * @param audience              this product's audience; tokens must list it in {@code aud}
 * @param provisioningClientId  the platform's service client ({@code client_id} claim)
 * @param defaultPlanCode       courier subscription plan given to a provisioned company
 * @param defaultMobile         company mobile when the platform sends none (the column is
 *                              required); blank means such a request is refused
 */
@ConfigurationProperties(prefix = "occpl.platform")
public record PlatformIntegrationProperties(
        boolean enabled,
        String issuerUri,
        String jwkSetUri,
        String audience,
        String provisioningClientId,
        String defaultPlanCode,
        String defaultMobile) {
}
