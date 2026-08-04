package com.courier.modules.pricing.application.strategy;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingResult;

/**
 * How a validated, weight-resolved {@link PricingContext} becomes a priced
 * {@link PricingResult} — the Strategy the module's brief asks for, one level above
 * {@code calculator.ChargeCalculator} (which prices one line; a strategy decides how the
 * lines are run and combined).
 *
 * <p>Today there is exactly one implementation, {@link StandardPricingStrategy}, which runs
 * every enabled {@code ChargeCalculator} in order. The seam exists for the future this
 * module is built to be reused by — a promotional or surge-pricing strategy would combine
 * the same calculators differently (or skip some) without
 * {@code PricingEngineImpl} or any calculator changing.
 */
public interface PricingStrategy {

    /** Whether this strategy applies to {@code context}. The factory tries strategies in
     * order and uses the first match. */
    boolean supports(PricingContext context);

    /** Runs the priced calculation. Only called when {@link #supports} is true. */
    PricingResult price(PricingContext context);
}
