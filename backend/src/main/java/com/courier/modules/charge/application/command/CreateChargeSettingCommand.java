package com.courier.modules.charge.application.command;

import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeType;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.charge.domain.CommissionType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateChargeSettingCommand(
        UUID chargeId,
        ChargeType chargeType,
        ChargeSlabType chargeSlabType,
        BigDecimal fromKm,
        BigDecimal toKm,
        BigDecimal fromKg,
        BigDecimal toKg,
        BigDecimal chargeValue,
        ChargeValueType chargeValueType,
        CommissionType commissionType,
        BigDecimal commissionValue
) {
}
