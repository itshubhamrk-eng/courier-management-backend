package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Copies the matched rate's insurance charge, when insurance is enabled <i>and</i> the
 * shipment declares a value — a shipment with no declared value has nothing to insure, so
 * charging for it anyway would price a cover nobody asked for.
 */
@Component
public class InsuranceCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.INSURANCE;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        BigDecimal declaredValue = context.command().declaredValue();
        return context.configuration().insuranceEnabled()
                && declaredValue != null && declaredValue.signum() > 0;
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        return context.matchedRate().getInsuranceCharge().setScale(2, RoundingMode.HALF_UP);
    }
}
