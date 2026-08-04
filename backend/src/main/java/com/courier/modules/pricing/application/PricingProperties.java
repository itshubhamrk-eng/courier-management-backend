package com.courier.modules.pricing.application;

import com.courier.modules.pricing.domain.PricingConfiguration;
import com.courier.modules.pricing.domain.RoundingRule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Deployment-wide pricing defaults, bound from {@code pricing.*}. Every field here is a
 * tunable the module's spec calls out under "Configuration" — none of it is per-company yet
 * (that would need a settings row, the way {@code company.CompanySettings} works, and no
 * caller has asked for company-level overrides).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    /** cm3-per-kg divisor for volumetric weight. 5000 is the road-freight convention most
     * Indian couriers use. */
    private BigDecimal volumetricDivisor = PricingConfiguration.DEFAULT_VOLUMETRIC_DIVISOR;

    private boolean fuelEnabled = true;

    private boolean odaEnabled = true;

    private boolean insuranceEnabled = true;

    private boolean discountEnabled = true;

    private RoundingRule roundingRule = RoundingRule.NEAREST_ONE;

    public PricingConfiguration toConfiguration() {
        return new PricingConfiguration(volumetricDivisor, fuelEnabled, odaEnabled,
                insuranceEnabled, discountEnabled, roundingRule);
    }
}
