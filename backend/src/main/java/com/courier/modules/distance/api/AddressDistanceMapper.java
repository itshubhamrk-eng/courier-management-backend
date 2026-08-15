package com.courier.modules.distance.api;

import com.courier.modules.distance.api.dto.AddressDistanceResponse;
import com.courier.modules.distance.domain.AddressDistance;
import org.springframework.stereotype.Component;

@Component
public class AddressDistanceMapper {

    public AddressDistanceResponse toResponse(AddressDistance d) {
        return new AddressDistanceResponse(
                d.getId(), d.getCompanyId(), d.getAddressType(), d.getFromId(), d.getToId(),
                d.getDistanceKm(), d.getDistanceMeter(), d.getRequiredTimeMinutes(), d.getCreatedAt());
    }
}
