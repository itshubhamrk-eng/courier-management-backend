package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OutForDeliveryRequest(
        @NotEmpty List<UUID> shipmentIds,
        @NotNull UUID deliveryUserId
) {
}
