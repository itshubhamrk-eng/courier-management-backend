package com.courier.modules.manifest.application;

import com.courier.modules.manifest.application.command.CreateManifestCommand;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.manifest.domain.ManifestCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The minimal Manifest module Shipment Movement needs underneath it — see
 * {@code Manifest}'s own class-level note and V19's migration comment for why this
 * exists at all. {@code COMPANY_ADMIN}/{@code BRANCH_MANAGER}/{@code OPERATOR} write,
 * any authenticated company user reads, matching Shipment Booking's own tiers exactly
 * (the same desks that book also manifest).
 */
public interface ManifestService {

    /**
     * Every id in {@code command.shipmentIds()} must exist, be {@code BOOKED}, and carry
     * exactly {@code command.bookingBranchId()}/{@code command.deliveryBranchId()} — a
     * manifest can never group shipments travelling different lanes. Transitions each to
     * {@code MANIFEST_CREATED} and stamps its {@code manifestId}.
     */
    Manifest create(CreateManifestCommand command);

    Manifest getById(UUID id);

    Page<Manifest> search(ManifestCriteria criteria, Pageable pageable);

    /**
     * Assigns the vehicle and driver and moves the manifest to {@code DISPATCHED}, after
     * checking (via {@code ShipmentService.findManifestCreatedShipments}) that it has at
     * least one shipment, then moves every one of them to {@code DISPATCHED} too (via
     * {@code ShipmentService.transitionToDispatched}) in the same transaction. Refuses a
     * manifest already dispatched, an inactive/foreign vehicle, or a driver id that is
     * not a real user of this company.
     */
    Manifest dispatch(UUID id, UUID vehicleId, UUID driverUserId);
}
