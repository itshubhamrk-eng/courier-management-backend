package com.courier.modules.pricing.application;

import com.courier.modules.pricing.application.command.PricingCommand;

/**
 * Prices a shipment. Stateless, thread-safe, and reusable by any caller that can build a
 * {@link PricingCommand} — Shipment Booking, Quotation, the mobile app (through the REST
 * API), and any future integration. Never persists anything; never depends on the Shipment
 * module or any other caller.
 *
 * <p>Flow: validate the route, validate serviceability, validate a rate exists, compute
 * volumetric and chargeable weight, run every enabled charge calculator, return the full
 * breakup. See {@code MEMORY/modules/pricing-engine.md}.
 */
public interface PricingEngine {

    /**
     * @throws com.courier.shared.exception.BusinessRuleException route not found or
     *         inactive, service type / package type / payment mode not found, pickup or
     *         delivery pincode not serviceable, no active rate for the combination, no
     *         weight slab covers the chargeable weight, or the actual weight (or a
     *         supplied dimension) is not greater than zero
     */
    PricingResult calculate(PricingCommand command);
}
