package com.courier.modules.rate.application.command;

import com.courier.modules.rate.domain.WeightUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code rateCode} is immutable and absent here; {@code expectedVersion} guards a 409. */
public record UpdateRateCommand(
        String rateName,
        UUID routeId,
        UUID serviceTypeId,
        UUID packageTypeId,
        UUID paymentModeId,
        BigDecimal minimumWeight,
        BigDecimal maximumWeight,
        WeightUnit weightUnit,
        BigDecimal baseRate,
        BigDecimal additionalWeight,
        BigDecimal additionalWeightRate,
        BigDecimal minimumCharge,
        BigDecimal fuelSurcharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal gstPercentage,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long expectedVersion
) {
}
