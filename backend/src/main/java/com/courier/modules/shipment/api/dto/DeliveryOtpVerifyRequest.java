package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryOtpVerifyRequest(
        @NotNull UUID deliveryUserId,
        @NotBlank String otp
) {
}
