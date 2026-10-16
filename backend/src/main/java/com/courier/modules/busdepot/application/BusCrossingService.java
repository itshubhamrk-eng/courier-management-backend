package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MSRTC crossing routes between bus depots / stations (any type to any type, never tied
 * to a branch), and the route each shipment actually used.
 */
public interface BusCrossingService {

    /** A route with its depots resolved for display. {@code routeId} is null for a shipment snapshot saved without a route. */
    record RouteView(UUID id, BusDepotStatus status, BusDepot from, BusDepot to, List<BusDepot> stops, Long version) {
    }

    record ShipmentCrossingView(UUID id, UUID shipmentId, UUID routeId, BusDepot from, BusDepot to, List<BusDepot> stops) {
    }

    List<RouteView> search(BusDepotStatus status, UUID depotId);

    RouteView getById(UUID id);

    /**
     * Saves the route {@code from -> to} via {@code stops}. A pair may have several routes
     * (different crossings); the same pair with the same stops in the same order is not
     * stored twice — the existing route is returned (and re-activated).
     */
    RouteView save(UUID fromDepotId, UUID toDepotId, List<UUID> stops);

    /** Changes the crossing points of one saved route; its start and end stay. */
    RouteView update(UUID id, List<UUID> stops);

    /**
     * Every active route for a trip, newest first — the booking desk picks the one it
     * needs. Either pass the depots directly, or the booking branch and delivery branch
     * — then any depot assigned to each branch counts.
     */
    List<RouteView> lookup(UUID fromDepotId, UUID toDepotId, UUID fromBranchId, UUID toBranchId);

    RouteView activate(UUID id);

    RouteView deactivate(UUID id);

    void delete(UUID id);

    /** Records (or replaces) the crossing a shipment travels; with {@code saveAsRoute} the route is kept for next time. */
    ShipmentCrossingView setForShipment(UUID shipmentId, UUID fromDepotId, UUID toDepotId, List<UUID> stops,
                                        boolean saveAsRoute);

    Optional<ShipmentCrossingView> getForShipment(UUID shipmentId);

    void clearForShipment(UUID shipmentId);
}
