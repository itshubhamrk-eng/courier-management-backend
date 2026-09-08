package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DispatchManifestRequest(
        @NotNull UUID manifestId,
        @NotNull UUID vehicleId,
        @NotNull UUID driverUserId,
        Instant departureTime,
        @DecimalMin(value = "0") BigDecimal fuelCost,
        @DecimalMin(value = "0") BigDecimal driverAdvance,
        @DecimalMin(value = "0") BigDecimal tollAmount,
        @DecimalMin(value = "0") BigDecimal otherAmount
) {
}
