package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DispatchManifestRequest(
        @NotNull UUID manifestId,
        @NotNull UUID vehicleId,
        /** Legacy: a picked company user. Optional now — send driverName/driverMobile instead. */
        UUID driverUserId,
        @Size(max = 120) String driverName,
        @Pattern(regexp = "^$|^[0-9+\\- ]{7,20}$", message = "Driver contact number is not valid") String driverMobile,
        Instant departureTime,
        @DecimalMin(value = "0") BigDecimal fuelCost,
        @DecimalMin(value = "0") BigDecimal driverAdvance,
        @DecimalMin(value = "0") BigDecimal tollAmount,
        @DecimalMin(value = "0") BigDecimal otherAmount
) {
}
