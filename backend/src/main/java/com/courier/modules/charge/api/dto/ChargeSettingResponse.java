package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full representation of one charge setting row. Nulls are serialised. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ChargeSettingResponse", description = "One charge setting row, in full")
public record ChargeSettingResponse(
        UUID id, UUID companyId, UUID chargeId,
        ChargeType chargeType, ChargeSlabType chargeSlabType,
        BigDecimal fromKm, BigDecimal toKm, BigDecimal fromKg, BigDecimal toKg,
        BigDecimal chargeValue, ChargeValueType chargeValueType,
        CommissionType commissionType, BigDecimal commissionValue,
        ChargeStatus status,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
