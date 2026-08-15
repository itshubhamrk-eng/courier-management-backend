package com.courier.modules.freight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Response of {@code POST /api/v1/freight-factors/calculate}: the matched cell and the
 * quoted freight. */
@Schema(name = "FreightCalculationResponse", description = "Quoted freight")
public record FreightCalculationResponse(
        UUID matchedFactorId,
        BigDecimal distanceKm,
        BigDecimal weight,
        BigDecimal factor,
        BigDecimal freight
) {
}
