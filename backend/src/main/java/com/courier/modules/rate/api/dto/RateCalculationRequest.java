package com.courier.modules.rate.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of {@code POST /api/v1/rates/calculate}. Prices a shipment without booking it. */
@Schema(name = "RateCalculationRequest", description = "Quote a shipment's freight")
public record RateCalculationRequest(

        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,
        @NotNull UUID serviceTypeId,
        @NotNull UUID packageTypeId,
        @NotNull UUID paymentModeId,

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal actualWeight,

        @Schema(description = "Defaults to today. Must fall within the matched rate's "
                + "effectiveFrom/effectiveTo window.")
        LocalDate bookingDate
) {
}
