package com.courier.modules.manifest.application.command;

import com.courier.modules.manifest.domain.DeliveryMode;

import java.util.List;
import java.util.UUID;

/**
 * {@code bookingBranchId}/{@code deliveryBranchId} are asserted against, not trusted
 * blindly — every shipment id supplied must already carry exactly these two branches
 * (see {@code ManifestServiceImpl.create}), so a manifest can never end up grouping
 * shipments travelling different lanes. {@code deliveryBranchId} is required for
 * {@link DeliveryMode#BRANCH_DELIVERY} and must be absent for
 * {@link DeliveryMode#DIRECT_COMPANY_DELIVERY} — validated in
 * {@code ManifestServiceImpl.create}, not here. {@code deliveryMode} null defaults to
 * {@code BRANCH_DELIVERY}.
 */
public record CreateManifestCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        DeliveryMode deliveryMode,
        String destinationCity,
        List<UUID> shipmentIds,
        String remarks
) {
}
