package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Copies the matched rate's handling charge. Unlike Fuel/ODA/Insurance/Discount, the
 * module's Configuration list has no enable/disable switch for handling — it is always on.
 */
@Component
public class HandlingCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.HANDLING;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return true;
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        return context.matchedRate().getHandlingCharge().setScale(2, RoundingMode.HALF_UP);
    }
}
