package com.courier.modules.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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

        @Schema(description = "Normally the shipment's real actual weight. A caller that "
                + "already knows its own chargeable weight (multi-item, its own volumetric "
                + "calculation already done) may feed that in here instead, with length/"
                + "width/height left null, to skip this engine re-deriving it from a single "
                + "blended figure — in which case totalActualWeight carries the real actual "
                + "weight separately.")
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal actualWeight,

        @Schema(description = "The shipment's real total actual weight, only when it "
                + "differs from actualWeight above (see its own doc). Optional — falls back "
                + "to actualWeight when omitted, correct for every ordinary caller. Only a "
                + "qty-level Applicable Charge (e.g. \"Hamali\") reads this, alongside "
                + "numberOfPackages.")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal totalActualWeight,

        @Schema(description = "Piece count. Optional, defaults to 1 — only matters for a "
                + "qty-level Applicable Charge (e.g. \"Hamali\"), which slab-matches on "
                + "totalActualWeight / this instead of chargeable weight, then multiplies "
                + "the matched slab's value by this.")
        @Min(value = 1, message = "must be at least 1")
        Integer numberOfPackages,

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
