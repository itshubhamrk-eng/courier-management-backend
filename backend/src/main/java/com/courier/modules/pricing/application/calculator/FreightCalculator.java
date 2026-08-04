package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import com.courier.modules.rate.domain.Rate;
import com.courier.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

/**
 * Matches {@link PricingContext#chargeableWeight()} against
 * {@link PricingContext#candidates()} (already narrowed to one Route + Service Type +
 * Package Type + Payment Mode by {@code validation.RateValidation}), the same slab-matching
 * algorithm {@code rate.application.RateServiceImpl.calculate} uses — exact slab, floored
 * below the lowest, overage above the highest, or a gap between two non-adjacent slabs.
 *
 * <p>This is the one calculator with a side effect: it sets
 * {@link PricingContext#matchedRate}, which every later calculator (Fuel, Handling, ODA,
 * Insurance, GST) reads its own surcharge fields off of.
 */
@Component
public class FreightCalculator implements ChargeCalculator {

    private static final int MONEY_SCALE = 2;

    @Override
    public ChargeType type() {
        return ChargeType.FREIGHT;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return true;
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        BigDecimal chargeableWeight = context.chargeableWeight();
        var candidates = context.candidates();

        Rate exact = candidates.stream()
                .filter(r -> r.covers(chargeableWeight)).findFirst().orElse(null);

        Rate matched;
        BigDecimal freight;

        if (exact != null) {
            matched = exact;
            freight = matched.getBaseRate();
        } else {
            Rate lowest = candidates.stream()
                    .min(Comparator.comparing(Rate::getMinimumWeight)).orElseThrow();
            Rate highest = candidates.stream()
                    .max(Comparator.comparing(Rate::getMaximumWeight)).orElseThrow();

            if (chargeableWeight.compareTo(lowest.getMinimumWeight()) < 0) {
                matched = lowest;
                freight = matched.getBaseRate();
            } else if (chargeableWeight.compareTo(highest.getMaximumWeight()) >= 0) {
                matched = highest;
                BigDecimal extra = chargeableWeight.subtract(matched.getMaximumWeight());
                BigDecimal units = extra.divide(matched.getAdditionalWeight(), 0, RoundingMode.CEILING);
                freight = matched.getBaseRate().add(units.multiply(matched.getAdditionalWeightRate()));
            } else {
                throw new BusinessRuleException(
                        ("No rate slab covers %s %s for this route, service type, package "
                                + "type and payment mode — there is a gap between the "
                                + "configured slabs.")
                                .formatted(chargeableWeight, candidates.get(0).getWeightUnit()));
            }
        }

        context.matchedRate(matched);
        freight = freight.max(matched.getMinimumCharge());
        return freight.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
