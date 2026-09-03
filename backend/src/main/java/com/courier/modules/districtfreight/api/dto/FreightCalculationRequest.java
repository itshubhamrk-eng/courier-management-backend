package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "FreightCalculationRequest", description = "From Station + destination pincode + chargeable weight")
public record FreightCalculationRequest(
        @NotNull UUID bookingBranchId,
        @NotBlank String destinationPincode,
        @NotNull @DecimalMin(value = "0.001", message = "Weight must be greater than zero") BigDecimal chargeableWeight
) {
}
