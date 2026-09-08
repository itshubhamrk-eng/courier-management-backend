package com.courier.modules.charge.api;

import com.courier.modules.charge.api.dto.ChargeResponse;
import com.courier.modules.charge.api.dto.ChargeSearchRequest;
import com.courier.modules.charge.api.dto.ChargeSummaryResponse;
import com.courier.modules.charge.api.dto.CreateChargeRequest;
import com.courier.modules.charge.api.dto.UpdateChargeRequest;
import com.courier.modules.charge.application.command.CreateChargeCommand;
import com.courier.modules.charge.application.command.UpdateChargeCommand;
import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeCriteria;
import com.courier.modules.charge.domain.ChargeSetting;
import org.springframework.stereotype.Component;

import java.util.List;

/** Wire contract <-> application/domain types for charges. */
@Component
public class ChargeMapper {

    private final ChargeSettingMapper settingMapper;

    public ChargeMapper(ChargeSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public CreateChargeCommand toCommand(CreateChargeRequest r) {
        return new CreateChargeCommand(r.chargeName(), r.serviceTypeId());
    }

    public UpdateChargeCommand toCommand(UpdateChargeRequest r) {
        return new UpdateChargeCommand(r.chargeName(), r.serviceTypeId(), r.version());
    }

    public ChargeCriteria toCriteria(ChargeSearchRequest r) {
        ChargeSearchRequest safe = r == null ? ChargeSearchRequest.empty() : r;
        return new ChargeCriteria(safe.serviceTypeId(), safe.status(), safe.search());
    }

    public ChargeResponse toResponse(Charge c, List<ChargeSetting> settings) {
        return new ChargeResponse(
                c.getId(), c.getCompanyId(), c.getChargeName(), c.getServiceTypeId(), c.getStatus(),
                settings.stream().map(settingMapper::toResponse).toList(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt(), c.getVersion());
    }

    public ChargeSummaryResponse toSummary(Charge c) {
        return new ChargeSummaryResponse(c.getId(), c.getChargeName(), c.getServiceTypeId(), c.getStatus(), c.getVersion());
    }
}
