package com.courier.modules.pricing.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything the Pricing Engine needs to price one shipment. Caller-agnostic: Shipment
 * Booking, Quotation, the mobile app and any future integration build the same command,
 * whether their own request DTO looks like {@code api.dto.PricingRequest} or not.
 *
 * @param bookingDate           defaults to today when null, same convention Rate Master's
 *                              calculator already established
 * @param discountPercentage    optional; not in the module's documented input list (which
 *                              has no discount field) but Discount is a required output
 *                              line, so a caller needs a way to ask for one — the same
 *                              honesty-note pattern Rate Master's optional
 *                              {@code bookingDate} followed. Percentage takes precedence
 *                              over {@code discountAmount} when both are supplied
 * @param discountAmount        optional flat discount, used when {@code discountPercentage}
 *                              is absent
 * @param freightFactorOverride optional, only meaningful on the Freight Factor fallback
 *                              path (see {@code PricingEngineImpl.priceByDistanceAndWeight}) —
 *                              a caller-supplied factor to price with instead of the grid's
 *                              own matched cell. Must be greater than or equal to the matched
 *                              factor; a smaller value is refused, since this exists to let a
 *                              desk raise a quote, never undercut the configured grid
 * @param totalActualWeight     the shipment's real total physical weight — deliberately
 *                              separate from {@code actualWeight}, which Shipment Booking's
 *                              own repricing helper ({@code ShipmentServiceImpl.priceIt})
 *                              feeds its already-known <b>chargeable</b> weight into (no
 *                              dims, to skip re-deriving volumetric weight from a single
 *                              blended figure). Only a qty-level {@code Charge} (e.g.
 *                              "Hamali") reads this, to slab-match on average per-piece
 *                              weight instead of total chargeable weight — see {@code
 *                              ApplicableChargesCalculator}
 * @param numberOfPackages      piece count; paired with {@code totalActualWeight} for the
 *                              same qty-level charge use — the average per-piece weight's
 *                              matched slab value is multiplied by this
 */
public record PricingCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String pickupPincode,
        String deliveryPincode,
        UUID serviceTypeId,
        UUID packageTypeId,
        UUID paymentModeId,
        BigDecimal actualWeight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal declaredValue,
        LocalDate bookingDate,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal freightFactorOverride,
        BigDecimal totalActualWeight,
        Integer numberOfPackages
) {
}
