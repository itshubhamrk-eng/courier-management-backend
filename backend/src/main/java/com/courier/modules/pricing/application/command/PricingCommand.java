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
        BigDecimal discountAmount
) {
}
