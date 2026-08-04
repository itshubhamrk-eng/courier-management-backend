package com.courier.modules.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Response of {@code POST /api/v1/pricing/calculate}. */
@Schema(name = "PricingResponse", description = "Matched route/rate, weights and the full charge breakup")
public record PricingResponse(
        UUID bookingBranchId,
        UUID deliveryBranchId,

        UUID matchedRouteId,
        String matchedRouteCode,

        UUID matchedRateId,
        String matchedRateCode,
        String matchedRateName,

        BigDecimal actualWeight,
        BigDecimal volumetricWeight,
        BigDecimal chargeableWeight,
        String weightUnit,

        ChargeBreakup chargeBreakup
) {
}
