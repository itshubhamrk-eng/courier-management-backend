package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DispatchOtpVerifyRequest(
        @NotNull UUID manifestId,
        @NotNull UUID driverUserId,
        @NotBlank String otp
) {
}
