package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutForDeliveryRequest(
        @NotEmpty List<UUID> shipmentIds,
        @NotNull UUID deliveryUserId,
        /** Optional — the delivery boy's own vehicle for this run. Validated active here
         *  (the controller, not ShipmentService — see its class doc for why). */
        UUID vehicleId,
        @DecimalMin(value = "0") BigDecimal fuelCost,
        @DecimalMin(value = "0") BigDecimal deliveryCharge
) {
}
