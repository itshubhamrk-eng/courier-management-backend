package com.courier.modules.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of {@code POST /api/v1/pricing/calculate}. */
@Schema(name = "PricingRequest", description = "Price a shipment without booking it")
public record PricingRequest(

        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,

        @NotBlank String pickupPincode,
        @NotBlank String deliveryPincode,

        @NotNull UUID serviceTypeId,
        @NotNull UUID packageTypeId,
        @NotNull UUID paymentModeId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal actualWeight,

        @Schema(description = "Centimetres. Optional; volumetric weight is 0 unless all "
                + "three of length/width/height are supplied.")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal length,

        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal width,

        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal height,

        @Schema(description = "Optional. Insurance is only charged when this is greater "
                + "than zero and insurance is enabled.")
        @DecimalMin(value = "0.0", message = "cannot be negative")
        BigDecimal declaredValue,

        @Schema(description = "Defaults to today. Must fall within the matched rate's "
                + "effectiveFrom/effectiveTo window.")
        LocalDate bookingDate,

        @Schema(description = "Not part of the module's documented input list — added "
                + "because Discount is a required output line and needs a source. Takes "
                + "precedence over discountAmount when both are supplied.")
        @DecimalMin(value = "0.0", message = "cannot be negative")
        BigDecimal discountPercentage,

        @DecimalMin(value = "0.0", message = "cannot be negative")
        BigDecimal discountAmount,

        @Schema(description = "Only meaningful on the Freight Factor fallback (no route/rate "
                + "for this lane): raises the matched grid cell's own factor before freight is "
                + "computed. Must be greater than or equal to the matched factor — a smaller "
                + "value is refused.")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal freightFactorOverride
) {
}
