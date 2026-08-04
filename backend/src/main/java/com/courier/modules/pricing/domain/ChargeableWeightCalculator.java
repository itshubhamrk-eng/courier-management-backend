package com.courier.modules.pricing.domain;

import java.math.BigDecimal;

/**
 * Chargeable weight — {@code MAX(actualWeight, volumetricWeight)} — the weight every
 * downstream step (rate slab matching, freight) prices against. This is the courier-
 * industry meaning of the term; it is a different quantity from
 * {@code rate.application.RateCalculationResult#chargeableWeight}, which is the weight
 * <i>billed after</i> slab/overage arithmetic on top of this one.
 */
public final class ChargeableWeightCalculator {

    private ChargeableWeightCalculator() {
    }

    public static BigDecimal calculate(BigDecimal actualWeight, BigDecimal volumetricWeight) {
        return actualWeight.max(volumetricWeight);
    }
}
