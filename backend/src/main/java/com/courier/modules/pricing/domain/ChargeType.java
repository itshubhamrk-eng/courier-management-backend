package com.courier.modules.pricing.domain;

/**
 * One line of a {@link PricingConfiguration}-driven charge breakup, in the order
 * {@link com.courier.modules.pricing.application.strategy.StandardPricingStrategy} runs
 * them — later types read the running subtotal earlier ones wrote.
 */
public enum ChargeType {
    FREIGHT,
    FUEL,
    HANDLING,
    ODA,
    INSURANCE,
    GST,
    DISCOUNT,
    ROUND_OFF
}
