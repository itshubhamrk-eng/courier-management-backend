package com.courier.modules.pricing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** Every charge line {@code PricingEngine} computed, in calculation order. */
@Schema(name = "ChargeBreakup", description = "Freight through Net Amount, in calculation order")
public record ChargeBreakup(
        BigDecimal freight,
        BigDecimal fuelCharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal applicableCharges,
        @Schema(description = "applicableCharges broken out by the charge module's own name "
                + "(e.g. \"Hamali\", \"Fuel Surcharge\"). Empty when no charge is configured "
                + "for this lane's service type.")
        List<ApplicableChargeLine> applicableChargeLines,
        BigDecimal gstAmount,
        BigDecimal discount,
        BigDecimal roundOff,
        BigDecimal netAmount
) {
    @Schema(name = "PricingApplicableChargeLine")
    public record ApplicableChargeLine(String chargeName, BigDecimal amount) {
    }
}
