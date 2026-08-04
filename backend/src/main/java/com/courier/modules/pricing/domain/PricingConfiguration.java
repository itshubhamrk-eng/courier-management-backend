package com.courier.modules.pricing.domain;

import java.math.BigDecimal;

/**
 * A resolved, immutable snapshot of the tunables that shape one pricing calculation:
 * the volumetric divisor and which non-freight charge lines are switched on. Built by
 * {@code PricingEngineImpl} from {@code PricingProperties} (the Spring-bound deployment
 * default) — the engine itself never reads a Spring property, so it stays a plain,
 * dependency-free value object any caller (Shipment, Quotation, a unit test) can build by
 * hand without a Spring context.
 *
 * @param volumetricDivisor cm3-per-kg divisor used by {@code VolumetricCalculator}
 * @param fuelEnabled       whether {@code FuelCalculator} charges the matched rate's fuel surcharge
 * @param odaEnabled        whether {@code ODAChargeCalculator} charges the matched rate's ODA charge
 * @param insuranceEnabled  whether {@code InsuranceCalculator} charges the matched rate's insurance charge
 * @param discountEnabled   whether {@code DiscountCalculator} applies the requested discount
 * @param roundingRule      how {@code RoundOffCalculator} rounds the final net amount
 */
public record PricingConfiguration(
        BigDecimal volumetricDivisor,
        boolean fuelEnabled,
        boolean odaEnabled,
        boolean insuranceEnabled,
        boolean discountEnabled,
        RoundingRule roundingRule
) {

    public static final BigDecimal DEFAULT_VOLUMETRIC_DIVISOR = new BigDecimal(5000);

    public static PricingConfiguration defaults() {
        return new PricingConfiguration(DEFAULT_VOLUMETRIC_DIVISOR, true, true, true, true,
                RoundingRule.NEAREST_ONE);
    }
}
