package com.courier.modules.pricing.domain;

import java.math.BigDecimal;

/**
 * Chargeable weight — {@code MAX(actualWeight, volumetricWeight, minimumChargeableWeight)}
 * — the weight every downstream step (rate slab matching, freight) prices against. This
 * is the courier-industry meaning of the term; it is a different quantity from
 * {@code rate.application.RateCalculationResult#chargeableWeight}, which is the weight
 * <i>billed after</i> slab/overage arithmetic on top of this one.
 *
 * <p>{@code minimumChargeableWeight} is {@code CompanySettings.defaultChargeableWeightKg}
 * — a real pricing floor (a 10kg booking still prices at the company's 15kg minimum), not
 * just the booking screen's item-grid prefill it doubles as.
 */
public final class ChargeableWeightCalculator {

    private ChargeableWeightCalculator() {
    }

    public static BigDecimal calculate(BigDecimal actualWeight, BigDecimal volumetricWeight) {
        return actualWeight.max(volumetricWeight);
    }

    public static BigDecimal calculate(BigDecimal actualWeight, BigDecimal volumetricWeight,
                                        BigDecimal minimumChargeableWeight) {
        BigDecimal chargeable = calculate(actualWeight, volumetricWeight);
        return minimumChargeableWeight == null ? chargeable : chargeable.max(minimumChargeableWeight);
    }
}
