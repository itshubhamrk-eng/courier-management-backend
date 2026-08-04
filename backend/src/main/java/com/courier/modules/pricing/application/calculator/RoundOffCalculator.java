package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The delta between {@link PricingContext#totalBeforeRoundOff()} and that amount rounded
 * per {@link com.courier.modules.pricing.domain.RoundingRule}. Can be negative (rounding
 * down) or positive (rounding up) — {@link PricingContext#netAmount()} adds it back on,
 * so Round Off is always visible as its own breakup line rather than silently folded into
 * Net Amount.
 */
@Component
public class RoundOffCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.ROUND_OFF;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return true;
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        BigDecimal preRound = context.totalBeforeRoundOff();
        BigDecimal rounded = context.configuration().roundingRule().apply(preRound);
        return rounded.subtract(preRound);
    }
}
