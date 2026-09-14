package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DispatchOtpRequestRequest(
        @NotNull UUID manifestId,
        @NotNull UUID driverUserId
) {
}
