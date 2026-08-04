package com.courier.modules.manifest.domain;

import java.util.UUID;

/** Every field optional; null means "do not constrain". */
public record ManifestCriteria(
        ManifestStatus status,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String search
) {
    public static ManifestCriteria none() {
        return new ManifestCriteria(null, null, null, null);
    }
}
