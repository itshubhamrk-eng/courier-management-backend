package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "FreightCalculationRequest", description = "From Station + destination pincode (+ optional Area) + chargeable weight")
public record FreightCalculationRequest(
        @NotNull UUID bookingBranchId,
        @NotBlank String destinationPincode,
        @Schema(description = "The specific Area of destinationPincode the operator picked, if any. "
                + "Resolves District/ODA off that exact pincode-area link rather than the pincode's legacy single area.")
        UUID destinationAreaId,
        @NotNull @DecimalMin(value = "0.001", message = "Weight must be greater than zero") BigDecimal chargeableWeight
) {
}
