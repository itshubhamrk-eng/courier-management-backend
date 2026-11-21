package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutForDeliveryRequest(
        @NotEmpty List<UUID> shipmentIds,
        /** Legacy picked user — optional now, send deliveryPersonName/Mobile instead. */
        UUID deliveryUserId,
        @Size(max = 120) String deliveryPersonName,
        @Pattern(regexp = "^$|^[0-9+\\- ]{7,20}$", message = "Contact number is not valid") String deliveryPersonMobile,
        /** Optional — the delivery boy's own vehicle for this run. Validated active here
         *  (the controller, not ShipmentService — see its class doc for why). */
        UUID vehicleId,
        @DecimalMin(value = "0") BigDecimal fuelCost,
        @DecimalMin(value = "0") BigDecimal deliveryCharge
) {
}
