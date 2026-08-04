package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Copies the matched rate's fuel surcharge, when fuel is enabled. */
@Component
public class FuelCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.FUEL;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return context.configuration().fuelEnabled();
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        return context.matchedRate().getFuelSurcharge().setScale(2, RoundingMode.HALF_UP);
    }
}
