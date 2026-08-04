package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Response of {@code POST /api/v1/rates/calculate}: the matched rate and its breakdown. */
@Schema(name = "RateCalculationResponse", description = "Quoted freight breakdown")
public record RateCalculationResponse(
        UUID matchedRateId,
        String matchedRateCode,
        String matchedRateName,
        BigDecimal chargeableWeight,
        WeightUnit weightUnit,
        BigDecimal freight,
        BigDecimal fuelSurcharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal gstPercentage,
        BigDecimal gstAmount,
        BigDecimal totalAmount
) {
}
