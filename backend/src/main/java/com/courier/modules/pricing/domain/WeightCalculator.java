package com.courier.modules.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The actual (physical, weighed) weight a shipment is priced against — the input to
 * {@link VolumetricCalculator} and {@link ChargeableWeightCalculator}, not derived from
 * either of them. Scaled to the same 3-decimal precision {@code rate.domain.Rate}'s weight
 * columns use, so a chargeable weight computed here compares cleanly against a rate slab.
 *
 * <p>Purely a normaliser — {@code actualWeight > 0} is
 * {@code com.courier.modules.pricing.application.validation.WeightValidation}'s job, and
 * runs before this is ever called, per the module's Validate-then-Calculate flow.
 */
public final class WeightCalculator {

    static final int WEIGHT_SCALE = 3;

    private WeightCalculator() {
    }

    public static BigDecimal normalise(BigDecimal actualWeight) {
        return actualWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
    }
}
