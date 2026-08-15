package com.courier.modules.freight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/freight-factors/calculate}. Distance is resolved
 * server-side from the branch pair via the Address Distance module — not passed by the
 * caller.
 */
@Schema(name = "FreightCalculationRequest", description = "Quote freight for a branch pair + weight")
public record FreightCalculationRequest(

        @NotNull UUID fromBranchId,
        @NotNull UUID toBranchId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal weight
) {
}
