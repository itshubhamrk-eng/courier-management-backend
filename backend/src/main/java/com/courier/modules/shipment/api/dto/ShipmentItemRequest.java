package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One packed item on a booking/update request. */
@Schema(name = "ShipmentItemRequest", description = "One packed item")
public record ShipmentItemRequest(
        @NotBlank @Size(max = 150) String itemName,
        @Min(1) Integer quantity,
        @NotNull @DecimalMin(value = "0.001") BigDecimal weight,
        @DecimalMin(value = "0.01") BigDecimal lengthCm,
        @DecimalMin(value = "0.01") BigDecimal widthCm,
        @DecimalMin(value = "0.01") BigDecimal heightCm,
        @DecimalMin(value = "0") BigDecimal declaredValue,
        Boolean fragile,
        Boolean dangerousGoods
) {
}
