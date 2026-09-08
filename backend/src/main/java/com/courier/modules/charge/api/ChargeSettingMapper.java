package com.courier.modules.charge.api;

import com.courier.modules.charge.api.dto.ChargeSettingResponse;
import com.courier.modules.charge.api.dto.CreateChargeSettingRequest;
import com.courier.modules.charge.api.dto.UpdateChargeSettingRequest;
import com.courier.modules.charge.application.command.CreateChargeSettingCommand;
import com.courier.modules.charge.application.command.UpdateChargeSettingCommand;
import com.courier.modules.charge.domain.ChargeSetting;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Wire contract <-> application/domain types for charge settings. */
@Component
public class ChargeSettingMapper {

    public CreateChargeSettingCommand toCommand(UUID chargeId, CreateChargeSettingRequest r) {
        return new CreateChargeSettingCommand(chargeId, r.chargeType(), r.chargeSlabType(),
                r.fromKm(), r.toKm(), r.fromKg(), r.toKg(),
                r.chargeValue(), r.chargeValueType(), r.commissionType(), r.commissionValue());
    }

    public UpdateChargeSettingCommand toCommand(UpdateChargeSettingRequest r) {
        return new UpdateChargeSettingCommand(r.chargeType(), r.chargeSlabType(),
                r.fromKm(), r.toKm(), r.fromKg(), r.toKg(),
                r.chargeValue(), r.chargeValueType(), r.commissionType(), r.commissionValue(), r.version());
    }

    public ChargeSettingResponse toResponse(ChargeSetting s) {
        return new ChargeSettingResponse(
                s.getId(), s.getCompanyId(), s.getChargeId(),
                s.getChargeType(), s.getChargeSlabType(),
                s.getFromKm(), s.getToKm(), s.getFromKg(), s.getToKg(),
                s.getChargeValue(), s.getChargeValueType(),
                s.getCommissionType(), s.getCommissionValue(),
                s.getStatus(),
                s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt(), s.getVersion());
    }
}
