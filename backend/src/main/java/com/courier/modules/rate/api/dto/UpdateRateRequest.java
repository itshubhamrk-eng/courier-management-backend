package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/rates/{id}}. Full replacement of the editable fields.
 * {@code rateCode} is immutable; {@code status} has its own endpoints. {@code version}
 * is required.
 */
@Schema(name = "UpdateRateRequest", description = "Full replacement of a rate's editable fields")
public record UpdateRateRequest(

        @NotBlank @Size(max = 150) String rateName,

        @NotNull UUID routeId,
        @NotNull UUID serviceTypeId,
        @NotNull UUID packageTypeId,
        @NotNull UUID paymentModeId,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal minimumWeight,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal maximumWeight,
        @NotNull WeightUnit weightUnit,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal baseRate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal additionalWeight,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal additionalWeightRate,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal minimumCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fuelSurcharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal handlingCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal odaCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal insuranceCharge,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "100.0", message = "cannot exceed 100") BigDecimal gstPercentage,

        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
