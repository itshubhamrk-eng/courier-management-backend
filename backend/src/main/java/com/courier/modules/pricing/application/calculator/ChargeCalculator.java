package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;

import java.math.BigDecimal;

/**
 * Strategy for computing one line of the charge breakup. Spring collects every bean
 * implementing this interface into {@code strategy.StandardPricingStrategy}, which sorts
 * them by {@link #order()} and runs them in sequence — a calculator reads whatever earlier
 * ones already wrote to {@link PricingContext} (GST reads the freight/fuel/handling/oda/
 * insurance subtotal; Round Off reads everything).
 */
public interface ChargeCalculator {

    ChargeType type();

    /** Execution order, ascending. Ties are not expected — each type appears once. */
    int order();

    /**
     * Whether this line runs at all for this request. A disabled calculator contributes
     * zero rather than being skipped, so {@link PricingContext#charge(ChargeType)} always
     * has an entry once the chain has run.
     */
    boolean isEnabled(PricingContext context);

    /** Computes this charge line. Only called when {@link #isEnabled} is true. */
    BigDecimal calculate(PricingContext context);
}
