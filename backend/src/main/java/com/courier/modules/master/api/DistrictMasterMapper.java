package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateDistrictRequest;
import com.courier.modules.master.api.dto.DistrictResponse;
import com.courier.modules.master.api.dto.UpdateDistrictRequest;
import com.courier.modules.master.application.MasterNameResolver;
import com.courier.modules.master.application.command.DistrictCommand;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.StateRepository;
import com.courier.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract to application/domain types for districts. */
@Component
@RequiredArgsConstructor
public class DistrictMasterMapper {

    private final MasterNameResolver names;
    private final StateRepository states;

    public DistrictCommand toCommand(CreateDistrictRequest r) {
        return new DistrictCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.stateId(), null);
    }

    public DistrictCommand toCommand(UpdateDistrictRequest r) {
        return new DistrictCommand(null, r.name(), r.description(), r.displayOrder(),
                r.stateId(), r.version());
    }

    public DistrictResponse toResponse(District d) {
        return toResponse(d, names.globalNamesById(states, List.of(d.getStateId())));
    }

    public DistrictResponse toResponse(District d, Map<UUID, String> stateNames) {
        return new DistrictResponse(d.getId(), d.getCompanyId(), d.getCode(), d.getName(),
                d.getDescription(), d.getStatus(), d.getDisplayOrder(),
                d.getStateId(), stateNames.get(d.getStateId()),
                d.getCreatedBy(), d.getCreatedAt(), d.getUpdatedBy(), d.getUpdatedAt(), d.getVersion());
    }

    public PageResponse<DistrictResponse> toPage(Page<District> page) {
        Map<UUID, String> stateNames = names.globalNamesById(states,
                page.getContent().stream().map(District::getStateId).toList());
        return PageResponse.from(page, district -> toResponse(district, stateNames));
    }
}
