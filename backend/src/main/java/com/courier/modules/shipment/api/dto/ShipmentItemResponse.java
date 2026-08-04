package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "ShipmentItemResponse", description = "One packed item")
public record ShipmentItemResponse(
        UUID id, String itemName, Integer quantity, BigDecimal weight,
        BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
        BigDecimal declaredValue, boolean fragile, boolean dangerousGoods
) {
}
