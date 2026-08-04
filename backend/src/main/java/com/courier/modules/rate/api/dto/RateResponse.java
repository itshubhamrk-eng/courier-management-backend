package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.RateStatus;
import com.courier.modules.rate.domain.WeightUnit;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full representation of a rate card row. Nulls are serialised. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RateResponse", description = "Rate card row, in full")
public record RateResponse(
        UUID id, UUID companyId, String rateCode, String rateName,
        UUID routeId, UUID serviceTypeId, UUID packageTypeId, UUID paymentModeId,
        BigDecimal minimumWeight, BigDecimal maximumWeight, WeightUnit weightUnit,
        BigDecimal baseRate, BigDecimal additionalWeight, BigDecimal additionalWeightRate,
        BigDecimal minimumCharge, BigDecimal fuelSurcharge, BigDecimal handlingCharge,
        BigDecimal odaCharge, BigDecimal insuranceCharge, BigDecimal gstPercentage,
        LocalDate effectiveFrom, LocalDate effectiveTo,
        RateStatus status,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
