package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/master/payment-modes/{id}}. */
@Schema(name = "UpdatePaymentModeRequest", description = "Full replacement of a payment mode's editable fields")
public record UpdatePaymentModeRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        Boolean collectAtBooking,
        Boolean collectAtDelivery,
        Boolean requiresCreditAccount,
        Boolean cashOnDelivery,
        @NotNull @PositiveOrZero Long version
) {
}
