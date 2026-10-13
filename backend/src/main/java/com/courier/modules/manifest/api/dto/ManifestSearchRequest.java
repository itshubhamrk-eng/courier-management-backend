package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.ManifestStatus;
import com.courier.modules.shipment.domain.FlowType;

import java.util.UUID;

public record ManifestSearchRequest(
        ManifestStatus status,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String search,
        FlowType flowType
) {
    public static ManifestSearchRequest empty() {
        return new ManifestSearchRequest(null, null, null, null, null);
    }
}
