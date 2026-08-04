package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.AreaResponse;
import com.courier.modules.master.api.dto.CreateAreaRequest;
import com.courier.modules.master.api.dto.UpdateAreaRequest;
import com.courier.modules.master.application.MasterNameResolver;
import com.courier.modules.master.application.command.AreaCommand;
import com.courier.modules.master.domain.Area;
import com.courier.modules.master.domain.CityRepository;
import com.courier.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract to application/domain types for areas. */
@Component
@RequiredArgsConstructor
public class AreaMasterMapper {

    private final MasterNameResolver names;
    private final CityRepository cities;

    public AreaCommand toCommand(CreateAreaRequest r) {
        return new AreaCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.cityId(), null);
    }

    public AreaCommand toCommand(UpdateAreaRequest r) {
        return new AreaCommand(null, r.name(), r.description(), r.displayOrder(),
                r.cityId(), r.version());
    }

    public AreaResponse toResponse(Area a) {
        return toResponse(a, names.globalNamesById(cities, List.of(a.getCityId())));
    }

    public AreaResponse toResponse(Area a, Map<UUID, String> cityNames) {
        return new AreaResponse(a.getId(), a.getCompanyId(), a.getCode(), a.getName(),
                a.getDescription(), a.getStatus(), a.getDisplayOrder(),
                a.getCityId(), cityNames.get(a.getCityId()),
                a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt(), a.getVersion());
    }

    public PageResponse<AreaResponse> toPage(Page<Area> page) {
        Map<UUID, String> cityNames = names.globalNamesById(cities,
                page.getContent().stream().map(Area::getCityId).toList());
        return PageResponse.from(page, area -> toResponse(area, cityNames));
    }
}
