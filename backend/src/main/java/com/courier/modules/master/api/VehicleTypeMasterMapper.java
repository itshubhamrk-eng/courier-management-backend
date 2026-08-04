package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateVehicleTypeRequest;
import com.courier.modules.master.api.dto.UpdateVehicleTypeRequest;
import com.courier.modules.master.api.dto.VehicleTypeResponse;
import com.courier.modules.master.application.command.VehicleTypeCommand;
import com.courier.modules.master.domain.VehicleType;
import com.courier.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Wire contract to application/domain types for vehicle types. */
@Component
public class VehicleTypeMasterMapper {

    public VehicleTypeCommand toCommand(CreateVehicleTypeRequest r) {
        return new VehicleTypeCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.capacityKg(), r.capacityCft(), r.wheelCount(), r.requiresPermit(), null);
    }

    public VehicleTypeCommand toCommand(UpdateVehicleTypeRequest r) {
        return new VehicleTypeCommand(null, r.name(), r.description(), r.displayOrder(),
                r.capacityKg(), r.capacityCft(), r.wheelCount(), r.requiresPermit(), r.version());
    }

    public VehicleTypeResponse toResponse(VehicleType v) {
        return new VehicleTypeResponse(v.getId(), v.getCompanyId(), v.getCode(), v.getName(),
                v.getDescription(), v.getStatus(), v.getDisplayOrder(),
                v.getCapacityKg(), v.getCapacityCft(), v.getWheelCount(), v.isRequiresPermit(),
                v.getCreatedBy(), v.getCreatedAt(), v.getUpdatedBy(), v.getUpdatedAt(), v.getVersion());
    }

    public PageResponse<VehicleTypeResponse> toPage(Page<VehicleType> page) {
        return PageResponse.from(page, this::toResponse);
    }
}
