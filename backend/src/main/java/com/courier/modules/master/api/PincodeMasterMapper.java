package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePincodeRequest;
import com.courier.modules.master.api.dto.PincodeResponse;
import com.courier.modules.master.api.dto.UpdatePincodeRequest;
import com.courier.modules.master.application.MasterNameResolver;
import com.courier.modules.master.application.command.PincodeCommand;
import com.courier.modules.master.domain.AreaRepository;
import com.courier.modules.master.domain.Pincode;
import com.courier.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wire contract to application/domain types for pincodes. */
@Component
@RequiredArgsConstructor
public class PincodeMasterMapper {

    private final MasterNameResolver names;
    private final AreaRepository areas;

    public PincodeCommand toCommand(CreatePincodeRequest r) {
        return new PincodeCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.areaId(), r.serviceable(), r.codAvailable(), r.prepaidAvailable(),
                r.pickupAvailable(), r.zone(), r.odaApplicable(), null);
    }

    public PincodeCommand toCommand(UpdatePincodeRequest r) {
        return new PincodeCommand(null, r.name(), r.description(), r.displayOrder(),
                r.areaId(), r.serviceable(), r.codAvailable(), r.prepaidAvailable(),
                r.pickupAvailable(), r.zone(), r.odaApplicable(), r.version());
    }

    public PincodeResponse toResponse(Pincode p) {
        return toResponse(p, names.globalNamesById(areas, List.of(p.getAreaId())));
    }

    public PincodeResponse toResponse(Pincode p, Map<UUID, String> areaNames) {
        return new PincodeResponse(p.getId(), p.getCompanyId(), p.getCode(), p.getName(),
                p.getDescription(), p.getStatus(), p.getDisplayOrder(),
                p.getAreaId(), areaNames.get(p.getAreaId()),
                p.isServiceable(), p.isCodAvailable(), p.isPrepaidAvailable(),
                p.isPickupAvailable(), p.getZone(), p.isOdaApplicable(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt(), p.getVersion());
    }

    public PageResponse<PincodeResponse> toPage(Page<Pincode> page) {
        Map<UUID, String> areaNames = names.globalNamesById(areas,
                page.getContent().stream().map(Pincode::getAreaId).toList());
        return PageResponse.from(page, pincode -> toResponse(pincode, areaNames));
    }
}
