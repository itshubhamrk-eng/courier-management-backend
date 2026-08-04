package com.courier.modules.manifest.api;

import com.courier.modules.manifest.api.dto.CreateManifestRequest;
import com.courier.modules.manifest.api.dto.CreateVehicleRequest;
import com.courier.modules.manifest.api.dto.ManifestResponse;
import com.courier.modules.manifest.api.dto.ManifestSearchRequest;
import com.courier.modules.manifest.api.dto.VehicleResponse;
import com.courier.modules.manifest.application.command.CreateManifestCommand;
import com.courier.modules.manifest.application.command.CreateVehicleCommand;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.manifest.domain.ManifestCriteria;
import com.courier.modules.manifest.domain.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class ManifestMapper {

    public CreateManifestCommand toCommand(CreateManifestRequest r) {
        return new CreateManifestCommand(r.bookingBranchId(), r.deliveryBranchId(), r.shipmentIds(), r.remarks());
    }

    public ManifestCriteria toCriteria(ManifestSearchRequest r) {
        ManifestSearchRequest safe = r == null ? ManifestSearchRequest.empty() : r;
        return new ManifestCriteria(safe.status(), safe.bookingBranchId(), safe.deliveryBranchId(), safe.search());
    }

    public ManifestResponse toResponse(Manifest m) {
        return new ManifestResponse(m.getId(), m.getManifestNumber(), m.getBookingBranchId(),
                m.getDeliveryBranchId(), m.getVehicleId(), m.getDriverUserId(), m.getStatus(),
                m.getDispatchedAt(), m.getCompletedAt(), m.getRemarks(),
                m.getCreatedAt(), m.getUpdatedAt(), m.getVersion());
    }

    public CreateVehicleCommand toCommand(CreateVehicleRequest r) {
        return new CreateVehicleCommand(r.vehicleNumber(), r.vehicleTypeId(), r.capacityKg(), r.remarks());
    }

    public VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getVehicleNumber(), v.getVehicleTypeId(),
                v.getCapacityKg(), v.getStatus(), v.getRemarks(), v.getVersion());
    }
}
