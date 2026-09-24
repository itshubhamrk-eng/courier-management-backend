package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Every field below {@code targetStatus}/{@code reason} is only used when the jump reuses a
 * real service method for that target — see {@code ShipmentService.overrideStatus}'s own doc.
 */
public record OverrideStatusRequest(
        @NotNull ShipmentStatus targetStatus,
        @NotBlank @Size(max = 500) String reason,

        // OUT_FOR_DELIVERY only
        UUID deliveryUserId,
        UUID vehicleId,
        @DecimalMin("0") BigDecimal fuelCost,
        @DecimalMin("0") BigDecimal deliveryCharge,

        // DELIVERED only
        @Size(max = 150) String receiverName,
        @Size(max = 10) String otp,
        @Size(max = 1000) String signatureUrl,
        @Size(max = 1000) String photoUrl
) {
}
