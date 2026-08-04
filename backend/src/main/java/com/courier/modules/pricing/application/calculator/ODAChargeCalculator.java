package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Copies the matched rate's out-of-delivery-area charge, when ODA is enabled. */
@Component
public class ODAChargeCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.ODA;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return context.configuration().odaEnabled();
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        return context.matchedRate().getOdaCharge().setScale(2, RoundingMode.HALF_UP);
    }
}
