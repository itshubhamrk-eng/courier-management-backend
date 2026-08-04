package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CountryResponse;
import com.courier.modules.master.api.dto.CreateCountryRequest;
import com.courier.modules.master.api.dto.UpdateCountryRequest;
import com.courier.modules.master.application.command.CountryCommand;
import com.courier.modules.master.domain.Country;
import com.courier.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Wire contract to application/domain types for countries. */
@Component
public class CountryMasterMapper {

    public CountryCommand toCommand(CreateCountryRequest r) {
        return new CountryCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.isoCode2(), r.isoCode3(), r.dialCode(), r.currencyCode(), null);
    }

    public CountryCommand toCommand(UpdateCountryRequest r) {
        // No code: it is immutable, and the update DTO has no field for it.
        return new CountryCommand(null, r.name(), r.description(), r.displayOrder(),
                r.isoCode2(), r.isoCode3(), r.dialCode(), r.currencyCode(), r.version());
    }

    public CountryResponse toResponse(Country c) {
        return new CountryResponse(c.getId(), c.getCompanyId(), c.getCode(), c.getName(),
                c.getDescription(), c.getStatus(), c.getDisplayOrder(),
                c.getIsoCode2(), c.getIsoCode3(), c.getDialCode(), c.getCurrencyCode(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt(), c.getVersion());
    }

    public PageResponse<CountryResponse> toPage(Page<Country> page) {
        return PageResponse.from(page, this::toResponse);
    }
}
