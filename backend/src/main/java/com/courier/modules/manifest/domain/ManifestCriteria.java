package com.courier.modules.manifest.domain;

import com.courier.modules.shipment.domain.FlowType;

import java.util.UUID;

/** Every field optional; null means "do not constrain" ({@code flowType} null = both flows). */
public record ManifestCriteria(
        ManifestStatus status,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String search,
        FlowType flowType
) {
    /** The original shape, before MSRTC: no flow filter. */
    public ManifestCriteria(ManifestStatus status, UUID bookingBranchId, UUID deliveryBranchId, String search) {
        this(status, bookingBranchId, deliveryBranchId, search, null);
    }

    public static ManifestCriteria none() {
        return new ManifestCriteria(null, null, null, null, null);
    }
}
