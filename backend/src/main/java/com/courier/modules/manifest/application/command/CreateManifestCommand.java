package com.courier.modules.manifest.application.command;

import java.util.List;
import java.util.UUID;

/**
 * {@code bookingBranchId}/{@code deliveryBranchId} are asserted against, not trusted
 * blindly — every shipment id supplied must already carry exactly these two branches
 * (see {@code ManifestServiceImpl.create}), so a manifest can never end up grouping
 * shipments travelling different lanes.
 */
public record CreateManifestCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        List<UUID> shipmentIds,
        String remarks
) {
}
