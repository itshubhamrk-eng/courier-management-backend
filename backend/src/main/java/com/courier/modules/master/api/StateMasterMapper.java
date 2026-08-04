package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateStateRequest;
import com.courier.modules.master.api.dto.StateResponse;
import com.courier.modules.master.api.dto.UpdateStateRequest;
import com.courier.modules.master.application.MasterNameResolver;
import com.courier.modules.master.application.command.StateCommand;
import com.courier.modules.master.domain.CountryRepository;
import com.courier.modules.master.domain.State;
import com.courier.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire contract to application/domain types for states.
 *
 * <p>{@code countryName} is filled from the parent so a list reads without a second
 * request per row. {@link MasterNameResolver} does it in one query for the whole page; the
 * single-row form asks for one id. A parent that is not in the map — because it is another
 * company's, or gone — leaves the name null rather than fabricating one.
 */
@Component
@RequiredArgsConstructor
public class StateMasterMapper {

    private final MasterNameResolver names;
    private final CountryRepository countries;

    public StateCommand toCommand(CreateStateRequest r) {
        return new StateCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.countryId(), r.gstStateCode(), null);
    }

    public StateCommand toCommand(UpdateStateRequest r) {
        return new StateCommand(null, r.name(), r.description(), r.displayOrder(),
                r.countryId(), r.gstStateCode(), r.version());
    }

    public StateResponse toResponse(State s) {
        return toResponse(s, names.globalNamesById(countries, List.of(s.getCountryId())));
    }

    public StateResponse toResponse(State s, Map<UUID, String> countryNames) {
        return new StateResponse(s.getId(), s.getCompanyId(), s.getCode(), s.getName(),
                s.getDescription(), s.getStatus(), s.getDisplayOrder(),
                s.getCountryId(), countryNames.get(s.getCountryId()), s.getGstStateCode(),
                s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt(), s.getVersion());
    }

    public PageResponse<StateResponse> toPage(Page<State> page) {
        Map<UUID, String> countryNames = names.globalNamesById(countries,
                page.getContent().stream().map(State::getCountryId).toList());
        return PageResponse.from(page, state -> toResponse(state, countryNames));
    }
}
