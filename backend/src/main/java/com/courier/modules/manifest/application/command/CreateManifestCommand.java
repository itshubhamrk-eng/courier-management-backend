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
 * {@code BRANCH_DELIVERY}. {@code hubTransfer} marks a Load Sheet whose {@code deliveryBranchId}
 * is a HUB (a stop en route, not the final delivery branch): shipments get only their
 * next stop set, and the hub assigns the real delivery branch from its own Load Sheet.
 */
public record CreateManifestCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        DeliveryMode deliveryMode,
        String destinationCity,
        List<UUID> shipmentIds,
        String remarks,
        boolean hubTransfer
) {
    public CreateManifestCommand(UUID bookingBranchId, UUID deliveryBranchId, DeliveryMode deliveryMode,
                                 String destinationCity, List<UUID> shipmentIds, String remarks) {
        this(bookingBranchId, deliveryBranchId, deliveryMode, destinationCity, shipmentIds, remarks, false);
    }
}
