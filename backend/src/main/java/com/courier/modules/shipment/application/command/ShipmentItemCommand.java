package com.courier.modules.shipment.application.command;

import java.math.BigDecimal;

/**
 * One packed item on a booking/update request.
 *
 * @param itemName        required
 * @param quantity        defaults to 1 when null
 * @param weight          per unit, kilograms — required, greater than zero
 * @param lengthCm        optional; all three dimensions are needed together for a
 *                        volumetric contribution, same convention
 *                        {@code pricing.domain.VolumetricCalculator} follows
 * @param widthCm         optional
 * @param heightCm        optional
 * @param declaredValue   optional
 * @param fragile         defaults to false
 * @param dangerousGoods  defaults to false
 */
public record ShipmentItemCommand(
        String itemName,
        Integer quantity,
        BigDecimal weight,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal declaredValue,
        Boolean fragile,
        Boolean dangerousGoods
) {
}
