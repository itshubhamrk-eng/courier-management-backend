package com.courier.modules.pdp.api;

import com.courier.modules.pdp.domain.PdpRateStatus;
import com.courier.modules.pdp.domain.PdpRateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Wire contract of {@code /api/v1/pdp-rates}. */
public final class PdpRateDtos {

    private PdpRateDtos() {
    }

    public record SlabRequest(
            @NotNull @DecimalMin("0") BigDecimal minWeight,
            @DecimalMin(value = "0", inclusive = false) BigDecimal maxWeight,
            @NotNull @DecimalMin("0") BigDecimal ratePerKg) {
    }

    /** Create ({@code branchId} and {@code rateType} required) or full replacement (both ignored if given and equal). */
    public record SaveRequest(
            UUID branchId,
            PdpRateType rateType,
            UUID districtId,
            @DecimalMin("0") BigDecimal minChargeableWeight,
            @DecimalMin("0") BigDecimal mathadiCharge,
            @DecimalMin("0") BigDecimal odaCharge,
            @DecimalMin("0") BigDecimal failedAttemptFee,
            @DecimalMin("0") BigDecimal returnFee,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull @Valid List<SlabRequest> slabs,
            @PositiveOrZero Long version) {
    }

    public record SlabResponse(UUID id, BigDecimal minWeight, BigDecimal maxWeight, BigDecimal ratePerKg) {
    }

    public record RateResponse(
            UUID id, UUID branchId, String branchCode, String branchName, PdpRateType rateType, UUID districtId,
            BigDecimal minChargeableWeight, BigDecimal mathadiCharge, BigDecimal odaCharge,
            BigDecimal failedAttemptFee, BigDecimal returnFee, LocalDate effectiveFrom, LocalDate effectiveTo,
            PdpRateStatus status, List<SlabResponse> slabs, Instant createdDate, Instant updatedDate, Long version) {
    }

    public record CalculateRequest(
            @NotNull UUID branchId,
            @NotNull PdpRateType rateType,
            UUID districtId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal actualWeight,
            boolean odaApplicable,
            LocalDate date) {
    }

    public record CalculateResponse(
            UUID rateId, PdpRateType rateType, BigDecimal actualWeight, BigDecimal chargeableWeight,
            BigDecimal ratePerKg, BigDecimal weightCharge, BigDecimal mathadiCharge, BigDecimal odaCharge,
            BigDecimal total) {
    }
}
