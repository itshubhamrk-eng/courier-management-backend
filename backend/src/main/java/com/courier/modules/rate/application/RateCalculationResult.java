package com.courier.modules.rate.application;

import com.courier.modules.rate.domain.Rate;

import java.math.BigDecimal;

/**
 * The priced outcome of {@link RateService#calculate}.
 *
 * @param matchedRate     the rate row the quote was priced against
 * @param chargeableWeight the weight actually billed — {@code actualWeight} floored at the
 *                        matched slab's minimum
 * @param freight         base freight: the slab's {@code baseRate} plus any overage beyond
 *                        its {@code maximumWeight}, floored at {@code minimumCharge}
 * @param fuelSurcharge   copied from the matched rate
 * @param handlingCharge  copied from the matched rate
 * @param odaCharge       copied from the matched rate
 * @param insuranceCharge copied from the matched rate
 * @param gstAmount       GST on {@code freight + fuelSurcharge + handlingCharge + odaCharge
 *                        + insuranceCharge}, at the matched rate's {@code gstPercentage}
 * @param totalAmount     everything above, summed
 */
public record RateCalculationResult(
        Rate matchedRate,
        BigDecimal chargeableWeight,
        BigDecimal freight,
        BigDecimal fuelSurcharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal gstAmount,
        BigDecimal totalAmount
) {
}
