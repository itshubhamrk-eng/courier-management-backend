package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/v1/charges/{chargeId}/settings}. {@code COMPANY_ADMIN} only.
 * Which of {@code fromKm}/{@code toKm}/{@code fromKg}/{@code toKg} are required depends
 * on {@code chargeType}/{@code chargeSlabType} — enforced server-side by
 * {@code ChargeSetting.applyInvariants}, not by bean validation, since the rule is
 * conditional rather than per-field.
 */
@Schema(name = "CreateChargeSettingRequest", description = "New charge setting under one charge")
public record CreateChargeSettingRequest(

        @NotNull ChargeType chargeType,

        @Schema(description = "Required when chargeType is SLAB") ChargeSlabType chargeSlabType,

        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromKm,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal toKm,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromKg,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal toKg,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal chargeValue,
        @NotNull ChargeValueType chargeValueType,

        @NotNull CommissionType commissionType,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal commissionValue
) {
}
