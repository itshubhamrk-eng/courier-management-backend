package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CityResponse;
import com.courier.modules.master.api.dto.CreateCityRequest;
import com.courier.modules.master.api.dto.UpdateCityRequest;
import com.courier.modules.master.application.MasterNameResolver;
import com.courier.modules.master.application.command.CityCommand;
import com.courier.modules.master.domain.City;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract to application/domain types for cities. */
@Component
@RequiredArgsConstructor
public class CityMasterMapper {

    private final MasterNameResolver names;
    private final DistrictRepository districts;

    public CityCommand toCommand(CreateCityRequest r) {
        return new CityCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.districtId(), r.metro(), r.cityTier(), null);
    }

    public CityCommand toCommand(UpdateCityRequest r) {
        return new CityCommand(null, r.name(), r.description(), r.displayOrder(),
                r.districtId(), r.metro(), r.cityTier(), r.version());
    }

    public CityResponse toResponse(City c) {
        return toResponse(c, names.globalNamesById(districts, List.of(c.getDistrictId())));
    }

    public CityResponse toResponse(City c, Map<UUID, String> districtNames) {
        return new CityResponse(c.getId(), c.getCompanyId(), c.getCode(), c.getName(),
                c.getDescription(), c.getStatus(), c.getDisplayOrder(),
                c.getDistrictId(), districtNames.get(c.getDistrictId()), c.isMetro(), c.getCityTier(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt(), c.getVersion());
    }

    public PageResponse<CityResponse> toPage(Page<City> page) {
        Map<UUID, String> districtNames = names.globalNamesById(districts,
                page.getContent().stream().map(City::getDistrictId).toList());
        return PageResponse.from(page, city -> toResponse(city, districtNames));
    }
}
