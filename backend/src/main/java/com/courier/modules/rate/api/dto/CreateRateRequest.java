package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.WeightUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/rates}. {@code COMPANY_ADMIN} only. A new rate always
 * starts ACTIVE — status has its own lifecycle endpoints.
 */
@Schema(name = "CreateRateRequest", description = "New rate card row within the caller's company")
public record CreateRateRequest(

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$",
                message = "3-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "RATE-PUNE-MUM-STD") String rateCode,

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
        @Schema(description = "Overage increment beyond maximumWeight") BigDecimal additionalWeight,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Charged per additionalWeight of overage") BigDecimal additionalWeightRate,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal minimumCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fuelSurcharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal handlingCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal odaCharge,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal insuranceCharge,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @DecimalMax(value = "100.0", message = "cannot exceed 100") BigDecimal gstPercentage,

        @NotNull LocalDate effectiveFrom,
        @Schema(description = "Null means open-ended") LocalDate effectiveTo
) {
}
