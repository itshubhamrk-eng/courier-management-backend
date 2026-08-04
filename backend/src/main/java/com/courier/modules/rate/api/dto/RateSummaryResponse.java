package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.RateStatus;
import com.courier.modules.rate.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Compact projection for list responses. */
@Schema(name = "RateSummaryResponse", description = "Rate card row, list projection")
public record RateSummaryResponse(
        UUID id, String rateCode, String rateName,
        UUID routeId, UUID serviceTypeId, UUID packageTypeId, UUID paymentModeId,
        BigDecimal minimumWeight, BigDecimal maximumWeight, WeightUnit weightUnit,
        BigDecimal baseRate, RateStatus status,
        LocalDate effectiveFrom, LocalDate effectiveTo, Long version
) {
}
