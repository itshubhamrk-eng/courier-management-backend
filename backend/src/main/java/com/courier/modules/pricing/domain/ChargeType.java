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
    /** Sum of every ACTIVE {@code com.courier.modules.charge.domain.Charge} configured
     *  against this booking's service type (e.g. "Hamali") — see
     *  {@code com.courier.modules.pricing.application.calculator.ApplicableChargesCalculator}. */
    APPLICABLE_CHARGES,
    GST,
    DISCOUNT,
    ROUND_OFF
}
