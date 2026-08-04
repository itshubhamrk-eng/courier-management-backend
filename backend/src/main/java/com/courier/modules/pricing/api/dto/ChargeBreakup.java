package com.courier.modules.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Every charge line {@code PricingEngine} computed, in calculation order. */
@Schema(name = "ChargeBreakup", description = "Freight through Net Amount, in calculation order")
public record ChargeBreakup(
        BigDecimal freight,
        BigDecimal fuelCharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal gstAmount,
        BigDecimal discount,
        BigDecimal roundOff,
        BigDecimal netAmount
) {
}
