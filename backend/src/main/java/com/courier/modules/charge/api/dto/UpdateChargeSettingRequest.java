package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Body of {@code PUT /api/v1/charges/{chargeId}/settings/{id}}. {@code version} required. */
@Schema(name = "UpdateChargeSettingRequest", description = "Full replacement of a charge setting's editable fields")
public record UpdateChargeSettingRequest(

        @NotNull ChargeType chargeType,
        ChargeSlabType chargeSlabType,

        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromKm,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal toKm,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal fromKg,
        @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal toKg,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal chargeValue,
        @NotNull ChargeValueType chargeValueType,

        @NotNull CommissionType commissionType,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative") BigDecimal commissionValue,

        @NotNull Long version
) {
}
