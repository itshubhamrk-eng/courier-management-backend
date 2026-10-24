package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** What a booking would cost, priced exactly as {@code POST /shipments} would price it. Nothing is saved. */
@Schema(name = "ShipmentPreviewResponse")
public record ShipmentPreviewResponse(
        BigDecimal actualWeight,
        BigDecimal volumetricWeight,
        BigDecimal chargeableWeight,
        String destinationCityName,
        String weightSlabLabel,
        BigDecimal ratePerKg,
        @Schema(description = "An E-Way Bill must be entered before booking (invoice value at or above the company threshold)")
        boolean ewayBillRequired,
        @Schema(description = "Booking branch wallet covers the net amount; null unless the payment mode collects at booking")
        Boolean walletSufficient,
        BigDecimal walletAvailable,
        @Schema(description = "Same breakup GET /shipments/{id}/charges returns after booking; shipmentId is null")
        ShipmentChargeResponse charges
) {
}
